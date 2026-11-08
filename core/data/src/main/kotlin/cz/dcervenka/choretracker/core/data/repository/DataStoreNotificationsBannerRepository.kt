package cz.dcervenka.choretracker.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import cz.dcervenka.choretracker.core.data.contract.NotificationsBannerRepository
import cz.dcervenka.choretracker.core.data.di.NotificationsBannerDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val DISMISSED_KEY = booleanPreferencesKey("dismissed")

@Singleton
class DataStoreNotificationsBannerRepository @Inject constructor(
    @NotificationsBannerDataStore private val dataStore: DataStore<Preferences>,
) : NotificationsBannerRepository {

    override fun observeDismissed(): Flow<Boolean> = dataStore.safeData
        .map { preferences -> preferences[DISMISSED_KEY] ?: false }

    // A failed write just leaves the banner up rather than crashing the app.
    override suspend fun dismiss() {
        try {
            dataStore.edit { preferences -> preferences[DISMISSED_KEY] = true }
        } catch (error: IOException) {
            Timber.e(error, "dismiss: write failed")
        }
    }
}
