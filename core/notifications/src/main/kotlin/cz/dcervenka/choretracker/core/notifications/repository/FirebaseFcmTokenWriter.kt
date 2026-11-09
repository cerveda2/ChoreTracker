package cz.dcervenka.choretracker.core.notifications.repository

import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val USERS_COLLECTION = "users"
private const val FCM_TOKEN_FIELD = "fcmToken"
private const val PUSH_TYPES_FIELD = "pushTypes"

/**
 * Talks to Firestore/FCM directly rather than going through RemoteHouseholdDataSource /
 * core-remote-firebase - see the "invite accepted notification" feature notes (plan file). Not
 * unit tested, same as FirebaseHouseholdDataSource's own I/O methods; verified by hand via the
 * emulator / real device steps instead.
 */
class FirebaseFcmTokenWriter @Inject constructor() : FcmTokenWriter {

    override suspend fun requestRegistration() {
        runCatching {
            awaitTask(FirebaseMessaging.getInstance().register())
        }.rethrowCancellation().onFailure { error ->
            Timber.w(error, "FirebaseFcmTokenWriter: failed to request registration")
        }
    }

    override suspend fun writeToken(userId: String, token: String, pushTypes: List<String>) {
        runCatching {
            awaitTask(
                FirebaseFirestore.getInstance()
                    .collection(USERS_COLLECTION)
                    .document(userId)
                    .set(
                        mapOf(
                            "userId" to userId,
                            FCM_TOKEN_FIELD to token,
                            PUSH_TYPES_FIELD to pushTypes,
                            "updatedAt" to Timestamp.now(),
                            // Lets onFcmTokenWritten tell which account registered this token last.
                            "fcmTokenUpdatedAt" to FieldValue.serverTimestamp(),
                        ),
                        SetOptions.merge(),
                    ),
            )
            // Handy for manually testing via Firebase Console -> Messaging -> "Send test message".
            // Only reaches logcat in debug builds - Timber's tree is only planted there.
            Timber.d("FirebaseFcmTokenWriter: registered fcmToken=$token for userId=$userId")
        }.rethrowCancellation().onFailure { error ->
            Timber.w(error, "FirebaseFcmTokenWriter: failed to write fcmToken")
        }
    }

    override suspend fun writePushTypes(userId: String, pushTypes: List<String>) {
        runCatching {
            awaitTask(
                FirebaseFirestore.getInstance()
                    .collection(USERS_COLLECTION)
                    .document(userId)
                    .set(mapOf(PUSH_TYPES_FIELD to pushTypes, "updatedAt" to Timestamp.now()), SetOptions.merge()),
            )
            Timber.d("FirebaseFcmTokenWriter: wrote pushTypes=$pushTypes for userId=$userId")
        }.rethrowCancellation().onFailure { error ->
            Timber.w(error, "FirebaseFcmTokenWriter: failed to write pushTypes")
        }
    }

    override suspend fun clearToken(userId: String) {
        runCatching {
            awaitTask(
                FirebaseFirestore.getInstance()
                    .collection(USERS_COLLECTION)
                    .document(userId)
                    .set(
                        mapOf(
                            FCM_TOKEN_FIELD to FieldValue.delete(),
                            "updatedAt" to Timestamp.now(),
                        ),
                        SetOptions.merge(),
                    ),
            )
            Timber.d("FirebaseFcmTokenWriter: cleared fcmToken for userId=$userId")
        }.rethrowCancellation().onFailure { error ->
            Timber.w(error, "FirebaseFcmTokenWriter: failed to clear fcmToken")
        }
    }
}

// Result.getOrElse/getOrNull don't special-case CancellationException - see the identical helper
// (and its rationale) in FirebaseHouseholdDataSource.kt, core/remote-firebase.
private fun <T> Result<T>.rethrowCancellation(): Result<T> =
    onFailure { if (it is CancellationException) throw it }

private suspend fun <T> awaitTask(task: Task<T>): T = suspendCancellableCoroutine { continuation ->
    task.addOnSuccessListener { continuation.resume(it) }
    task.addOnFailureListener { continuation.resumeWithException(it) }
}
