package cz.dcervenka.choretracker.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import cz.dcervenka.choretracker.core.data.contract.ReminderSettingsRepository
import cz.dcervenka.choretracker.core.data.di.ReminderSettingsDataStore
import cz.dcervenka.choretracker.core.model.settings.ReminderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val ENABLED_KEY = booleanPreferencesKey("enabled")
private val HOUR_KEY = intPreferencesKey("hour")
private val MINUTE_KEY = intPreferencesKey("minute")

@Singleton
class DataStoreReminderSettingsRepository @Inject constructor(
    @ReminderSettingsDataStore private val dataStore: DataStore<Preferences>,
) : ReminderSettingsRepository {

    override fun observeSettings(): Flow<ReminderSettings> = dataStore.data.map(::toReminderSettings)

    override suspend fun getSettings(): ReminderSettings = toReminderSettings(dataStore.data.first())

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[ENABLED_KEY] = enabled }
    }

    override suspend fun setReminderTime(hour: Int, minute: Int) {
        dataStore.edit { preferences ->
            preferences[HOUR_KEY] = hour
            preferences[MINUTE_KEY] = minute
        }
    }

    private fun toReminderSettings(preferences: Preferences): ReminderSettings {
        val defaults = ReminderSettings()
        return ReminderSettings(
            enabled = preferences[ENABLED_KEY] ?: defaults.enabled,
            hour = preferences[HOUR_KEY] ?: defaults.hour,
            minute = preferences[MINUTE_KEY] ?: defaults.minute,
        )
    }
}
