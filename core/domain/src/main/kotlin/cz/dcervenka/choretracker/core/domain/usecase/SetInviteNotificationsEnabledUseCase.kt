package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.InviteNotificationSettingsRepository
import cz.dcervenka.choretracker.core.model.auth.AuthState
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SetInviteNotificationsEnabledUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val inviteNotificationSettingsRepository: InviteNotificationSettingsRepository,
) {
    suspend operator fun invoke(enabled: Boolean) {
        val userId = (authRepository.authState.first() as? AuthState.Authenticated)?.user?.id ?: return
        inviteNotificationSettingsRepository.setEnabled(userId, enabled)
    }
}
