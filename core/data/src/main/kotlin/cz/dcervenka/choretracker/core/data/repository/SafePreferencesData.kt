package cz.dcervenka.choretracker.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException

// DataStore's data Flow throws IOException on a corrupted/unreadable preferences file -
// fall back to defaults instead of letting that kill this Flow's collectors permanently.
internal val DataStore<Preferences>.safeData: Flow<Preferences>
    get() = data.catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
