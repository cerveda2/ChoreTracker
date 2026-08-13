package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.InviteNotificationSettingsRepository
import javax.inject.Inject

class SetInviteNotificationsEnabledUseCase @Inject constructor(
    private val inviteNotificationSettingsRepository: InviteNotificationSettingsRepository,
) {
    suspend operator fun invoke(enabled: Boolean) = inviteNotificationSettingsRepository.setEnabled(enabled)
}
