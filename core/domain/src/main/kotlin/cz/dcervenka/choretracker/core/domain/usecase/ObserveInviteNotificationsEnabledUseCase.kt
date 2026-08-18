package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.InviteNotificationSettingsRepository
import cz.dcervenka.choretracker.core.model.auth.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class ObserveInviteNotificationsEnabledUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val inviteNotificationSettingsRepository: InviteNotificationSettingsRepository,
) {
    operator fun invoke(): Flow<Boolean> = authRepository.authState.flatMapLatest { authState ->
        val userId = (authState as? AuthState.Authenticated)?.user?.id
        if (userId == null) {
            flowOf(true)
        } else {
            inviteNotificationSettingsRepository.observeEnabled(userId)
        }
    }
}
