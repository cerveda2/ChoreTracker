package cz.dcervenka.choretracker.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import cz.dcervenka.choretracker.core.data.contract.InviteNotificationSettingsRepository
import cz.dcervenka.choretracker.core.data.di.InviteNotificationSettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val DEFAULT_ENABLED = true

@Singleton
class DataStoreInviteNotificationSettingsRepository @Inject constructor(
    @InviteNotificationSettingsDataStore private val dataStore: DataStore<Preferences>,
) : InviteNotificationSettingsRepository {

    // DataStore's data Flow throws IOException on a corrupted/unreadable preferences file -
    // fall back to defaults instead of letting that kill this Flow's collectors permanently.
    private val safeData: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    override fun observeEnabled(userId: String): Flow<Boolean> =
        safeData.map { preferences -> preferences[enabledKey(userId)] ?: DEFAULT_ENABLED }

    override suspend fun isEnabled(userId: String): Boolean =
        safeData.first()[enabledKey(userId)] ?: DEFAULT_ENABLED

    override suspend fun setEnabled(userId: String, enabled: Boolean) {
        dataStore.edit { preferences -> preferences[enabledKey(userId)] = enabled }
    }

    private fun enabledKey(userId: String) = booleanPreferencesKey("enabled_$userId")
}
