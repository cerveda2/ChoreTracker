package cz.dcervenka.choretracker.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import cz.dcervenka.choretracker.core.data.contract.UpdatePromptRepository
import cz.dcervenka.choretracker.core.data.di.UpdatePromptDataStore
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val LAST_OFFERED_VERSION_KEY = intPreferencesKey("last_offered_version")

@Singleton
class DataStoreUpdatePromptRepository @Inject constructor(
    @UpdatePromptDataStore private val dataStore: DataStore<Preferences>,
) : UpdatePromptRepository {

    override suspend fun getLastOfferedVersion(): Int? = dataStore.safeData.first()[LAST_OFFERED_VERSION_KEY]

    // A failed write just means the same version is offered again on a later start.
    override suspend fun setLastOfferedVersion(versionCode: Int) {
        try {
            dataStore.edit { preferences -> preferences[LAST_OFFERED_VERSION_KEY] = versionCode }
        } catch (error: IOException) {
            Timber.e(error, "setLastOfferedVersion: write failed")
        }
    }
}
