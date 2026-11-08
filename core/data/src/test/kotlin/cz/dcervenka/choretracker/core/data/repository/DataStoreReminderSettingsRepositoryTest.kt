package cz.dcervenka.choretracker.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.model.settings.ReminderSettings
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
    fun `defaults to disabled at 09_00 when nothing has been persisted yet`() = runTest {
        val settings = repository.getSettings()

        assertThat(settings.enabled).isFalse()
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
        repository.setEnabled(true)
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

    @Test
    fun `no chores are muted when nothing has been persisted yet`() = runTest {
        assertThat(repository.getMutedChoreIds()).isEmpty()
    }

    @Test
    fun `setChoreMuted adds and removes a chore without touching the others`() = runTest {
        repository.setChoreMuted("chore-1", muted = true)
        repository.setChoreMuted("chore-2", muted = true)
        repository.setChoreMuted("chore-1", muted = false)

        assertThat(repository.getMutedChoreIds()).containsExactly("chore-2")
        assertThat(repository.observeMutedChoreIds().first()).containsExactly("chore-2")
    }

    @Test
    fun `clearMutedChoreIds unmutes every chore`() = runTest {
        repository.setChoreMuted("chore-1", muted = true)
        repository.setChoreMuted("chore-2", muted = true)

        repository.clearMutedChoreIds()

        assertThat(repository.getMutedChoreIds()).isEmpty()
    }

    @Test
    fun `muting a chore leaves the reminder settings unchanged`() = runTest {
        repository.setEnabled(true)
        repository.setReminderTime(hour = 20, minute = 30)

        repository.setChoreMuted("chore-1", muted = true)

        assertThat(repository.getSettings()).isEqualTo(ReminderSettings(enabled = true, hour = 20, minute = 30))
    }
}
