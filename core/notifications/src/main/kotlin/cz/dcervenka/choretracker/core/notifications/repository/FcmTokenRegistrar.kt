package cz.dcervenka.choretracker.core.notifications.repository

import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.notifications.di.NotificationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps this device's FCM token written onto the signed-in user's `users/{uid}.fcmToken` field,
 * so the "invite accepted" Cloud Function can push a notification to a household owner. Mirrors
 * LocalSyncRepository's authState.flatMapLatest {...}.launchIn(scope) pattern - see
 * core/sync/.../repository/LocalSyncRepository.kt.
 */
@Singleton
class FcmTokenRegistrar @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenWriter: FcmTokenWriter,
    @NotificationScope private val scope: CoroutineScope,
) {

    init {
        authRepository.authState
            .flatMapLatest { authState ->
                val user = (authState as? AuthState.Authenticated)?.user
                if (user == null || user.isPreview) {
                    emptyFlow()
                } else {
                    flow { emit(registerCurrentToken(user.id)) }
                }
            }
            .launchIn(scope)
    }

    /**
     * Called by [cz.dcervenka.choretracker.core.notifications.service.InviteAcceptedMessagingService]
     * when FCM rotates the device token.
     */
    fun onTokenRefreshed(token: String) {
        scope.launch {
            val user = (authRepository.authState.first() as? AuthState.Authenticated)?.user
            if (user != null && !user.isPreview) tokenWriter.writeToken(user.id, token)
        }
    }

    private suspend fun registerCurrentToken(userId: String) {
        val token = tokenWriter.fetchCurrentDeviceToken() ?: return
        tokenWriter.writeToken(userId, token)
    }
}
