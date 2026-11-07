package cz.dcervenka.choretracker.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import cz.dcervenka.choretracker.core.data.contract.NotificationsBannerRepository
import cz.dcervenka.choretracker.core.data.di.NotificationsBannerDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val DISMISSED_KEY = booleanPreferencesKey("dismissed")

@Singleton
class DataStoreNotificationsBannerRepository @Inject constructor(
    @NotificationsBannerDataStore private val dataStore: DataStore<Preferences>,
) : NotificationsBannerRepository {

    override fun observeDismissed(): Flow<Boolean> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> preferences[DISMISSED_KEY] ?: false }

    override suspend fun dismiss() {
        dataStore.edit { preferences -> preferences[DISMISSED_KEY] = true }
    }
}
