package cz.dcervenka.choretracker.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import cz.dcervenka.choretracker.core.data.contract.ThemeSettingsRepository
import cz.dcervenka.choretracker.core.data.di.ThemeSettingsDataStore
import cz.dcervenka.choretracker.core.model.settings.ThemeMode
import cz.dcervenka.choretracker.core.model.settings.ThemeSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val MODE_KEY = stringPreferencesKey("mode")
private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")

@Singleton
class DataStoreThemeSettingsRepository @Inject constructor(
    @ThemeSettingsDataStore private val dataStore: DataStore<Preferences>,
) : ThemeSettingsRepository {

    // DataStore's data Flow throws IOException on a corrupted/unreadable preferences file -
    // fall back to defaults instead of letting that kill this Flow's collectors permanently.
    private val safeData: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    override fun observeSettings(): Flow<ThemeSettings> = safeData.map(::toThemeSettings)

    override suspend fun setMode(mode: ThemeMode) {
        dataStore.edit { preferences -> preferences[MODE_KEY] = mode.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[DYNAMIC_COLOR_KEY] = enabled }
    }

    private fun toThemeSettings(preferences: Preferences): ThemeSettings {
        val defaults = ThemeSettings()
        val mode = preferences[MODE_KEY]?.let { stored ->
            runCatching { ThemeMode.valueOf(stored) }.getOrDefault(defaults.mode)
        } ?: defaults.mode
        return ThemeSettings(
            mode = mode,
            dynamicColor = preferences[DYNAMIC_COLOR_KEY] ?: defaults.dynamicColor,
        )
    }
}
