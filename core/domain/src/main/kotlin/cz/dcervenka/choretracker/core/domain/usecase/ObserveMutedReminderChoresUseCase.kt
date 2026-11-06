package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.ReminderSettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveMutedReminderChoresUseCase @Inject constructor(
    private val reminderSettingsRepository: ReminderSettingsRepository,
) {
    operator fun invoke(): Flow<Set<String>> = reminderSettingsRepository.observeMutedChoreIds()
}
