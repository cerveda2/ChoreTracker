package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.ReminderSettingsRepository
import cz.dcervenka.choretracker.core.model.settings.ReminderSettings
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveReminderSettingsUseCase @Inject constructor(
    private val reminderSettingsRepository: ReminderSettingsRepository,
) {
    operator fun invoke(): Flow<ReminderSettings> = reminderSettingsRepository.observeSettings()
}
