package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.ReminderSettingsRepository
import javax.inject.Inject

class SetReminderEnabledUseCase @Inject constructor(
    private val reminderSettingsRepository: ReminderSettingsRepository,
) {
    suspend operator fun invoke(enabled: Boolean) = reminderSettingsRepository.setEnabled(enabled)
}
