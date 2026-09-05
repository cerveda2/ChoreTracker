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

// Pre-per-user-scoping, this was the only key this DataStore file ever wrote (see git history).
// Kept as a read-only fallback so a device that already had this toggled off doesn't silently
// revert to DEFAULT_ENABLED just because it has no "enabled_$userId" entry yet.
private val LEGACY_GLOBAL_ENABLED_KEY = booleanPreferencesKey("enabled")

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
        safeData.map { preferences -> resolveEnabled(preferences, userId) }

    override suspend fun isEnabled(userId: String): Boolean =
        resolveEnabled(safeData.first(), userId)

    override suspend fun setEnabled(userId: String, enabled: Boolean) {
        dataStore.edit { preferences -> preferences[enabledKey(userId)] = enabled }
    }

    private fun resolveEnabled(preferences: Preferences, userId: String): Boolean =
        preferences[enabledKey(userId)] ?: preferences[LEGACY_GLOBAL_ENABLED_KEY] ?: DEFAULT_ENABLED

    private fun enabledKey(userId: String) = booleanPreferencesKey("enabled_$userId")
}
