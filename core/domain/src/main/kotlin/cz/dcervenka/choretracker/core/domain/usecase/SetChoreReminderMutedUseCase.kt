package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.ReminderSettingsRepository
import javax.inject.Inject

class SetChoreReminderMutedUseCase @Inject constructor(
    private val reminderSettingsRepository: ReminderSettingsRepository,
) {
    suspend operator fun invoke(choreId: String, muted: Boolean) =
        reminderSettingsRepository.setChoreMuted(choreId, muted)
}
