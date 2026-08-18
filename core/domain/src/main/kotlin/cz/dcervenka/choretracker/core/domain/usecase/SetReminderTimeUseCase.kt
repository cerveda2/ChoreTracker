package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.ReminderSettingsRepository
import javax.inject.Inject

class SetReminderTimeUseCase @Inject constructor(
    private val reminderSettingsRepository: ReminderSettingsRepository,
) {
    suspend operator fun invoke(hour: Int, minute: Int) = reminderSettingsRepository.setReminderTime(hour, minute)
}
