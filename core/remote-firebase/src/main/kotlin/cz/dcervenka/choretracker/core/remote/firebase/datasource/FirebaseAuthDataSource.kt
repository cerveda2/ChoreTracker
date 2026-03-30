package cz.dcervenka.choretracker.core.remote.firebase.datasource

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.model.auth.AppUser
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.remote.contract.RemoteAuthDataSource
import cz.dcervenka.choretracker.core.remote.firebase.runtime.FirebaseRuntimeConfigurator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Singleton
class FirebaseAuthDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : RemoteAuthDataSource {

    init {
        FirebaseRuntimeConfigurator.configure(context)
    }

    private val firebaseAuth: FirebaseAuth?
        get() = if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseAuth.getInstance()

    override val isConfigured: Boolean
        get() = firebaseAuth != null

    override val authState: Flow<AuthState> = callbackFlow {
        val auth = firebaseAuth
        if (auth == null) {
            trySend(AuthState.RequiresConfiguration)
            close()
            return@callbackFlow
        }

        val listener = FirebaseAuth.AuthStateListener { firebase ->
            val user = firebase.currentUser
            if (user == null) {
                trySend(AuthState.SignedOut)
            } else {
                trySend(
                    AuthState.Authenticated(
                        AppUser(
                            id = user.uid,
                            email = user.email,
                            displayName = user.displayName ?: user.email.orEmpty(),
                        ),
                    ),
                )
            }
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signIn(email: String, password: String): EmptyResult {
        val auth = firebaseAuth ?: return AppResult.Error("Firebase isn't configured yet.")
        if (email.isBlank()) return AppResult.Error("Email is required.")
        if (password.isBlank()) return AppResult.Error("Password is required.")
        return suspendCancellableCoroutine { continuation ->
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { continuation.resume(AppResult.Success(Unit)) }
                .addOnFailureListener { continuation.resume(AppResult.Error(it.message ?: "Unable to sign in.", it)) }
        }
    }

    override suspend fun signUp(email: String, password: String, displayName: String): EmptyResult {
        val auth = firebaseAuth ?: return AppResult.Error("Firebase isn't configured yet.")
        if (displayName.isBlank()) return AppResult.Error("Display name is required.")
        if (email.isBlank()) return AppResult.Error("Email is required.")
        if (password.isBlank()) return AppResult.Error("Password is required.")
        return suspendCancellableCoroutine { continuation ->
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val user = result.user
                    if (user == null) {
                        continuation.resume(AppResult.Error("The Firebase account was created without a user instance."))
                    } else {
                        user.updateProfile(
                            UserProfileChangeRequest.Builder()
                                .setDisplayName(displayName)
                                .build(),
                        )
                            .addOnSuccessListener { continuation.resume(AppResult.Success(Unit)) }
                            .addOnFailureListener {
                                continuation.resume(AppResult.Error(it.message ?: "Unable to update profile.", it))
                            }
                    }
                }
                .addOnFailureListener { continuation.resume(AppResult.Error(it.message ?: "Unable to create account.", it)) }
        }
    }

    override suspend fun signOut(): EmptyResult {
        val auth = firebaseAuth ?: return AppResult.Success(Unit)
        auth.signOut()
        return AppResult.Success(Unit)
    }
}
