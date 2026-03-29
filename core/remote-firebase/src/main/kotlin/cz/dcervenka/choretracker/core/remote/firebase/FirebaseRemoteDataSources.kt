package cz.dcervenka.choretracker.core.remote.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.model.AppUser
import cz.dcervenka.choretracker.core.model.AuthState
import cz.dcervenka.choretracker.core.model.PendingSyncOperation
import cz.dcervenka.choretracker.core.remote.contract.RemoteAuthDataSource
import cz.dcervenka.choretracker.core.remote.contract.RemoteHouseholdDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private object FirebaseRuntimeConfigurator {
    @Volatile
    private var configured = false

    fun configure(context: Context) {
        if (configured) return
        synchronized(this) {
            if (configured) return
            if (FirebaseApp.getApps(context).isEmpty()) return

            val auth = FirebaseAuth.getInstance()
            val firestore = FirebaseFirestore.getInstance()

            if (BuildConfig.USE_FIREBASE_EMULATORS) {
                auth.useEmulator(
                    BuildConfig.FIREBASE_AUTH_EMULATOR_HOST,
                    BuildConfig.FIREBASE_AUTH_EMULATOR_PORT,
                )
                firestore.useEmulator(
                    BuildConfig.FIREBASE_FIRESTORE_EMULATOR_HOST,
                    BuildConfig.FIREBASE_FIRESTORE_EMULATOR_PORT,
                )
            }

            firestore.firestoreSettings = firestoreSettings {
                isPersistenceEnabled = true
            }
            configured = true
        }
    }
}

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
        return suspendCancellableCoroutine { continuation ->
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { continuation.resume(AppResult.Success(Unit)) }
                .addOnFailureListener { continuation.resume(AppResult.Error(it.message ?: "Unable to sign in.", it)) }
        }
    }

    override suspend fun signUp(email: String, password: String, displayName: String): EmptyResult {
        val auth = firebaseAuth ?: return AppResult.Error("Firebase isn't configured yet.")
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

@Singleton
class FirebaseHouseholdDataSource @Inject constructor(
    @param:ApplicationContext context: Context,
) : RemoteHouseholdDataSource {

    init {
        FirebaseRuntimeConfigurator.configure(context)
    }

    override suspend fun pushPendingOperations(operations: List<PendingSyncOperation>): EmptyResult =
        AppResult.Success(Unit)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FirebaseRemoteModule {
    @Binds
    abstract fun bindRemoteAuthDataSource(impl: FirebaseAuthDataSource): RemoteAuthDataSource

    @Binds
    abstract fun bindRemoteHouseholdDataSource(impl: FirebaseHouseholdDataSource): RemoteHouseholdDataSource
}
