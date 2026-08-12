package cz.dcervenka.choretracker.core.notifications.repository

import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
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

/**
 * Talks to Firestore/FCM directly rather than going through RemoteHouseholdDataSource /
 * core-remote-firebase - see the "invite accepted notification" feature notes (plan file). Not
 * unit tested, same as FirebaseHouseholdDataSource's own I/O methods; verified by hand via the
 * emulator / real device steps instead.
 */
class FirebaseFcmTokenWriter @Inject constructor() : FcmTokenWriter {

    override suspend fun fetchCurrentDeviceToken(): String? = runCatching {
        awaitTask(FirebaseMessaging.getInstance().token)
    }.rethrowCancellation().getOrElse { error ->
        Timber.w(error, "FirebaseFcmTokenWriter: failed to fetch device token")
        null
    }?.also { token ->
        // Handy for manually testing via Firebase Console -> Messaging -> "Send test message".
        // Only reaches logcat in debug builds - Timber's tree is only planted there.
        Timber.d("FirebaseFcmTokenWriter: current device token=$token")
    }

    override suspend fun writeToken(userId: String, token: String) {
        runCatching {
            awaitTask(
                FirebaseFirestore.getInstance()
                    .collection(USERS_COLLECTION)
                    .document(userId)
                    .set(
                        mapOf(
                            "userId" to userId,
                            FCM_TOKEN_FIELD to token,
                            "updatedAt" to Timestamp.now(),
                        ),
                        SetOptions.merge(),
                    ),
            )
            Timber.d("FirebaseFcmTokenWriter: registered fcmToken for userId=$userId")
        }.rethrowCancellation().onFailure { error ->
            Timber.w(error, "FirebaseFcmTokenWriter: failed to write fcmToken")
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
