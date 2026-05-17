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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
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

        val listener = FirebaseAuth.IdTokenListener { firebase ->
            val user = firebase.currentUser
            if (user == null) {
                Timber.d("authState: signed out")
                trySend(AuthState.SignedOut)
            } else {
                Timber.d("authState: authenticated uid=${user.uid} displayName=${user.displayName}")
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
        auth.addIdTokenListener(listener)
        awaitClose { auth.removeIdTokenListener(listener) }
    }.distinctUntilChanged()

    override suspend fun signIn(email: String, password: String): EmptyResult {
        Timber.d("signIn: email=$email")
        val validationError = validateCredentials(
            email = email,
            password = password,
        )
        return validationError ?: firebaseAuth?.let { auth ->
            suspendCancellableCoroutine { continuation ->
                auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        Timber.d("signIn: success")
                        continuation.resume(AppResult.Success(Unit))
                    }
                    .addOnFailureListener { throwable ->
                        Timber.e(throwable, "signIn: failed")
                        continuation.resume(
                            AppResult.Error(
                                throwable.message ?: "Unable to sign in.",
                                throwable,
                            ),
                        )
                    }
            }
        } ?: AppResult.Error("Firebase isn't configured yet.")
    }

    override suspend fun signUp(email: String, password: String, displayName: String): EmptyResult {
        Timber.d("signUp: email=$email displayName=$displayName")
        val validationError = validateSignUpInputs(
            email = email,
            password = password,
            displayName = displayName,
        )
        return validationError ?: firebaseAuth?.let { auth ->
            suspendCancellableCoroutine { continuation ->
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener { result ->
                        val user = result.user
                        if (user == null) {
                            continuation.resume(
                                AppResult.Error(
                                    "The Firebase account was created without a user instance.",
                                ),
                            )
                        } else {
                            user.updateProfile(
                                UserProfileChangeRequest.Builder()
                                    .setDisplayName(displayName)
                                    .build(),
                            )
                                .addOnSuccessListener {
                                    Timber.d("signUp: profile updated uid=${user.uid}, forcing token refresh")
                                    user.getIdToken(true)
                                        .addOnCompleteListener {
                                            Timber.d("signUp: token refreshed uid=${user.uid}")
                                            continuation.resume(AppResult.Success(Unit))
                                        }
                                }
                                .addOnFailureListener { throwable ->
                                    Timber.e(throwable, "signUp: profile update failed")
                                    continuation.resume(
                                        AppResult.Error(
                                            throwable.message ?: "Unable to update profile.",
                                            throwable,
                                        ),
                                    )
                                }
                        }
                    }
                    .addOnFailureListener { throwable ->
                        continuation.resume(
                            AppResult.Error(
                                throwable.message ?: "Unable to create account.",
                                throwable,
                            ),
                        )
                    }
            }
        } ?: AppResult.Error("Firebase isn't configured yet.")
    }

    override suspend fun updateDisplayName(displayName: String): EmptyResult {
        val sanitizedName = displayName.trim()
        val user = firebaseAuth?.currentUser
        return when {
            sanitizedName.isBlank() -> AppResult.Error("Display name is required.")
            user == null -> AppResult.Error("Sign in first.")
            else -> suspendCancellableCoroutine { continuation ->
                user.updateProfile(
                    UserProfileChangeRequest.Builder()
                        .setDisplayName(sanitizedName)
                        .build(),
                )
                    .addOnSuccessListener {
                        user.getIdToken(true)
                            .addOnCompleteListener {
                                Timber.d("updateDisplayName: success uid=${user.uid}")
                                continuation.resume(AppResult.Success(Unit))
                            }
                    }
                    .addOnFailureListener { throwable ->
                        Timber.e(throwable, "updateDisplayName: failed")
                        continuation.resume(
                            AppResult.Error(
                                throwable.message ?: "Unable to update profile.",
                                throwable,
                            ),
                        )
                    }
            }
        }
    }

    override suspend fun signOut(): EmptyResult {
        val auth = firebaseAuth ?: return AppResult.Success(Unit)
        auth.signOut()
        return AppResult.Success(Unit)
    }

    private fun validateCredentials(email: String, password: String): EmptyResult? = when {
        email.isBlank() -> AppResult.Error("Email is required.")
        password.isBlank() -> AppResult.Error("Password is required.")
        else -> null
    }

    private fun validateSignUpInputs(
        email: String,
        password: String,
        displayName: String,
    ): EmptyResult? = when {
        displayName.isBlank() -> AppResult.Error("Display name is required.")
        else -> validateCredentials(
            email = email,
            password = password,
        )
    }
}
