package cz.dcervenka.choretracker.core.data.contract

import cz.dcervenka.choretracker.core.model.settings.ReminderSettings
import kotlinx.coroutines.flow.Flow

interface ReminderSettingsRepository {
    fun observeSettings(): Flow<ReminderSettings>

    suspend fun getSettings(): ReminderSettings

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setReminderTime(hour: Int, minute: Int)

    // Kept out of ReminderSettings so muting a chore doesn't trigger ChoreReminderScheduler's
    // settings-change reschedule.
    fun observeMutedChoreIds(): Flow<Set<String>>

    suspend fun getMutedChoreIds(): Set<String>

    suspend fun setChoreMuted(choreId: String, muted: Boolean)

    suspend fun clearMutedChoreIds()
}
