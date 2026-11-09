package cz.dcervenka.choretracker.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import cz.dcervenka.choretracker.core.data.contract.RequestNotificationSettingsRepository
import cz.dcervenka.choretracker.core.data.di.RequestNotificationSettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreRequestNotificationSettingsRepository @Inject constructor(
    @RequestNotificationSettingsDataStore private val dataStore: DataStore<Preferences>,
) : RequestNotificationSettingsRepository {

    private val safeData: Flow<Preferences> = dataStore.safeData

    override fun observeEnabled(userId: String): Flow<Boolean> =
        safeData.map { preferences -> preferences[enabledKey(userId)] ?: true }

    override suspend fun isEnabled(userId: String): Boolean = safeData.first()[enabledKey(userId)] ?: true

    override suspend fun setEnabled(userId: String, enabled: Boolean) {
        dataStore.edit { preferences -> preferences[enabledKey(userId)] = enabled }
    }

    private fun enabledKey(userId: String) = booleanPreferencesKey("enabled_$userId")
}
