package cz.dcervenka.choretracker.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.model.settings.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreThemeSettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: DataStoreThemeSettingsRepository

    @Before
    fun setUp() {
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempFolder.newFile("test.preferences_pb") },
        )
        repository = DataStoreThemeSettingsRepository(dataStore)
    }

    @Test
    fun `defaults to system mode with dynamic colour off when nothing has been persisted yet`() = runTest {
        val settings = repository.observeSettings().first()

        assertThat(settings.mode).isEqualTo(ThemeMode.SYSTEM)
        assertThat(settings.dynamicColor).isFalse()
    }

    @Test
    fun `setMode persists and is reflected by observeSettings`() = runTest {
        repository.setMode(ThemeMode.DARK)

        assertThat(repository.observeSettings().first().mode).isEqualTo(ThemeMode.DARK)
    }

    @Test
    fun `setDynamicColor persists independently of mode`() = runTest {
        repository.setMode(ThemeMode.LIGHT)
        repository.setDynamicColor(true)

        val settings = repository.observeSettings().first()
        assertThat(settings.mode).isEqualTo(ThemeMode.LIGHT)
        assertThat(settings.dynamicColor).isTrue()
    }

    @Test
    fun `an unrecognised stored mode value falls back to the default instead of crashing`() = runTest {
        dataStore.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[androidx.datastore.preferences.core.stringPreferencesKey("mode")] = "NOT_A_REAL_MODE"
            }
        }

        assertThat(repository.observeSettings().first().mode).isEqualTo(ThemeMode.SYSTEM)
    }
}
