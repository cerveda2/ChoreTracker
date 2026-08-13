package cz.dcervenka.choretracker.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreReminderSettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: DataStoreReminderSettingsRepository

    @Before
    fun setUp() {
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempFolder.newFile("test.preferences_pb") },
        )
        repository = DataStoreReminderSettingsRepository(dataStore)
    }

    @Test
    fun `defaults to enabled at 09_00 when nothing has been persisted yet`() = runTest {
        val settings = repository.getSettings()

        assertThat(settings.enabled).isTrue()
        assertThat(settings.hour).isEqualTo(9)
        assertThat(settings.minute).isEqualTo(0)
    }

    @Test
    fun `setEnabled persists and is reflected by getSettings`() = runTest {
        repository.setEnabled(false)

        assertThat(repository.getSettings().enabled).isFalse()
    }

    @Test
    fun `setReminderTime persists hour and minute independently of enabled`() = runTest {
        repository.setReminderTime(hour = 20, minute = 30)

        val settings = repository.getSettings()
        assertThat(settings.hour).isEqualTo(20)
        assertThat(settings.minute).isEqualTo(30)
        assertThat(settings.enabled).isTrue()
    }

    @Test
    fun `observeSettings emits the latest persisted value`() = runTest {
        repository.setEnabled(false)
        repository.setReminderTime(hour = 18, minute = 15)

        val settings = repository.observeSettings().first()
        assertThat(settings.enabled).isFalse()
        assertThat(settings.hour).isEqualTo(18)
        assertThat(settings.minute).isEqualTo(15)
    }
}
