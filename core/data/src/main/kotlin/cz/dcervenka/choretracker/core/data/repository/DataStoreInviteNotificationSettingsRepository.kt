package cz.dcervenka.choretracker.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import cz.dcervenka.choretracker.core.data.contract.InviteNotificationSettingsRepository
import cz.dcervenka.choretracker.core.data.di.InviteNotificationSettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val ENABLED_KEY = booleanPreferencesKey("enabled")
private const val DEFAULT_ENABLED = true

@Singleton
class DataStoreInviteNotificationSettingsRepository @Inject constructor(
    @InviteNotificationSettingsDataStore private val dataStore: DataStore<Preferences>,
) : InviteNotificationSettingsRepository {

    override fun observeEnabled(): Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[ENABLED_KEY] ?: DEFAULT_ENABLED }

    override suspend fun isEnabled(): Boolean = dataStore.data.first()[ENABLED_KEY] ?: DEFAULT_ENABLED

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[ENABLED_KEY] = enabled }
    }
}
