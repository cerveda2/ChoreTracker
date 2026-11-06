package cz.dcervenka.choretracker.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import cz.dcervenka.choretracker.core.data.contract.ReminderSettingsRepository
import cz.dcervenka.choretracker.core.data.di.ReminderSettingsDataStore
import cz.dcervenka.choretracker.core.model.settings.ReminderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val ENABLED_KEY = booleanPreferencesKey("enabled")
private val HOUR_KEY = intPreferencesKey("hour")
private val MINUTE_KEY = intPreferencesKey("minute")
private val MUTED_CHORE_IDS_KEY = stringSetPreferencesKey("muted_chore_ids")

@Singleton
class DataStoreReminderSettingsRepository @Inject constructor(
    @ReminderSettingsDataStore private val dataStore: DataStore<Preferences>,
) : ReminderSettingsRepository {

    // DataStore's data Flow throws IOException on a corrupted/unreadable preferences file -
    // fall back to defaults instead of letting that kill this Flow's collectors permanently.
    private val safeData: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    override fun observeSettings(): Flow<ReminderSettings> = safeData.map(::toReminderSettings)

    override suspend fun getSettings(): ReminderSettings = toReminderSettings(safeData.first())

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[ENABLED_KEY] = enabled }
    }

    override suspend fun setReminderTime(hour: Int, minute: Int) {
        dataStore.edit { preferences ->
            preferences[HOUR_KEY] = hour
            preferences[MINUTE_KEY] = minute
        }
    }

    override fun observeMutedChoreIds(): Flow<Set<String>> = safeData.map(::toMutedChoreIds)

    override suspend fun getMutedChoreIds(): Set<String> = toMutedChoreIds(safeData.first())

    override suspend fun setChoreMuted(choreId: String, muted: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[MUTED_CHORE_IDS_KEY].orEmpty()
            preferences[MUTED_CHORE_IDS_KEY] = if (muted) current + choreId else current - choreId
        }
    }

    private fun toMutedChoreIds(preferences: Preferences): Set<String> = preferences[MUTED_CHORE_IDS_KEY].orEmpty()

    private fun toReminderSettings(preferences: Preferences): ReminderSettings {
        val defaults = ReminderSettings()
        return ReminderSettings(
            enabled = preferences[ENABLED_KEY] ?: defaults.enabled,
            hour = preferences[HOUR_KEY] ?: defaults.hour,
            minute = preferences[MINUTE_KEY] ?: defaults.minute,
        )
    }
}
