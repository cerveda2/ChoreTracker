package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.InviteNotificationSettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveInviteNotificationsEnabledUseCase @Inject constructor(
    private val inviteNotificationSettingsRepository: InviteNotificationSettingsRepository,
) {
    operator fun invoke(): Flow<Boolean> = inviteNotificationSettingsRepository.observeEnabled()
}
