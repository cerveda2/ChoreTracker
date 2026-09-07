package cz.dcervenka.choretracker.core.data.contract

import cz.dcervenka.choretracker.core.model.settings.ThemeMode
import cz.dcervenka.choretracker.core.model.settings.ThemeSettings
import kotlinx.coroutines.flow.Flow

interface ThemeSettingsRepository {
    fun observeSettings(): Flow<ThemeSettings>

    suspend fun setMode(mode: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)
}
