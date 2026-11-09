package cz.dcervenka.choretracker.core.notifications.repository

import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.InviteNotificationSettingsRepository
import cz.dcervenka.choretracker.core.data.contract.PushTokenRepository
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.notifications.di.NotificationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

private val SIGN_OUT_CLEAR_TIMEOUT = 3.seconds

// Push types in users/{uid}.pushTypes, as the Cloud Functions check them.
private const val PUSH_TYPE_INVITE = "invite"

/**
 * Keeps this device's FCM installation ID written onto the signed-in user's
 * `users/{uid}.fcmToken` field, so the "invite accepted" Cloud Function can push a notification to
 * whoever created the invite. Mirrors LocalSyncRepository's authState.flatMapLatest {...}.launchIn(scope)
 * pattern - see core/sync/.../repository/LocalSyncRepository.kt.
 *
 * Next to the token it writes `pushTypes`: the pushes this build can show and the user wants. The
 * invite toggle only removes "invite" from it and leaves the token in place, so other push types
 * keep arriving. Builds before this one cleared the token instead and wrote no `pushTypes`, which
 * the functions treat as "invite" only.
 */
@Singleton
class FcmTokenRegistrar @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenWriter: FcmTokenWriter,
    private val inviteNotificationSettingsRepository: InviteNotificationSettingsRepository,
    @NotificationScope private val scope: CoroutineScope,
) : PushTokenRepository {

    // Set by clearCurrentUserToken until the sign-out it precedes lands, so a registration
    // callback arriving in between can't write the token straight back.
    @Volatile
    private var signingOutUserId: String? = null

    // Signing out is handled by clearCurrentUserToken, called before it happens: by the time
    // authState reports it, rules already deny the write to the signed-out user's doc.
    init {
        authRepository.authState
            .flatMapLatest { authState ->
                val user = (authState as? AuthState.Authenticated)?.user
                if (user == null || user.isPreview) {
                    signingOutUserId = null
                    emptyFlow()
                } else {
                    inviteNotificationSettingsRepository.observeEnabled(user.id).map { enabled -> user.id to enabled }
                }
            }
            .onEach { (userId, enabled) -> applyTokenState(userId, enabled) }
            // retry, not catch: catch would let an unexpected exception permanently end this
            // subscription with only a debug-only log line - retry logs and re-subscribes instead,
            // so a transient failure doesn't silently and permanently stop FCM token registration.
            .retry { error ->
                Timber.e(error, "FcmTokenRegistrar: token-state subscription failed, retrying")
                delay(5.seconds)
                true
            }
            .launchIn(scope)
    }

    /**
     * Called by [cz.dcervenka.choretracker.core.notifications.service.InviteAcceptedMessagingService]
     * when FCM (re-)registers this device's installation ID - either in response to
     * [applyTokenState] calling [FcmTokenWriter.requestRegistration], or on a later auto-rotation.
     */
    fun onIdRegistered(installationId: String) {
        scope.launch {
            val user = (authRepository.authState.first() as? AuthState.Authenticated)?.user
            if (user == null || user.isPreview || user.id == signingOutUserId) return@launch
            tokenWriter.writeToken(
                user.id,
                installationId,
                pushTypes(inviteEnabled = inviteNotificationSettingsRepository.isEnabled(user.id)),
            )
        }
    }

    // Bounded so signing out offline isn't held up for long. An unfinished write stays queued under
    // this user and is sent if they sign in here again; if another account signs in instead, the
    // onFcmTokenWritten function moves the token off this one.
    override suspend fun clearCurrentUserToken() {
        val user = (authRepository.authState.first() as? AuthState.Authenticated)?.user
        if (user == null || user.isPreview) return
        signingOutUserId = user.id
        withTimeoutOrNull(SIGN_OUT_CLEAR_TIMEOUT) { tokenWriter.clearToken(user.id) }
    }

    // pushTypes first, so a token registered for the first time never lands without it.
    private suspend fun applyTokenState(userId: String, inviteEnabled: Boolean) {
        tokenWriter.writePushTypes(userId, pushTypes(inviteEnabled))
        tokenWriter.requestRegistration()
    }

    private fun pushTypes(inviteEnabled: Boolean): List<String> =
        if (inviteEnabled) listOf(PUSH_TYPE_INVITE) else emptyList()
}
