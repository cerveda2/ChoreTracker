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

class DataStoreInviteNotificationSettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: DataStoreInviteNotificationSettingsRepository

    @Before
    fun setUp() {
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempFolder.newFile("test.preferences_pb") },
        )
        repository = DataStoreInviteNotificationSettingsRepository(dataStore)
    }

    @Test
    fun `defaults to enabled when nothing has been persisted yet`() = runTest {
        assertThat(repository.isEnabled()).isTrue()
    }

    @Test
    fun `setEnabled persists and is reflected by isEnabled`() = runTest {
        repository.setEnabled(false)

        assertThat(repository.isEnabled()).isFalse()
    }

    @Test
    fun `observeEnabled emits the latest persisted value`() = runTest {
        repository.setEnabled(false)

        assertThat(repository.observeEnabled().first()).isFalse()

        repository.setEnabled(true)

        assertThat(repository.observeEnabled().first()).isTrue()
    }
}
