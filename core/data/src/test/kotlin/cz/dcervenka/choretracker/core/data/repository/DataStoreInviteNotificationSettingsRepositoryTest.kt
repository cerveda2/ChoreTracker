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
        assertThat(repository.isEnabled("user-1")).isTrue()
    }

    @Test
    fun `setEnabled persists and is reflected by isEnabled`() = runTest {
        repository.setEnabled("user-1", false)

        assertThat(repository.isEnabled("user-1")).isFalse()
    }

    @Test
    fun `observeEnabled emits the latest persisted value`() = runTest {
        repository.setEnabled("user-1", false)

        assertThat(repository.observeEnabled("user-1").first()).isFalse()

        repository.setEnabled("user-1", true)

        assertThat(repository.observeEnabled("user-1").first()).isTrue()
    }

    @Test
    fun `settings are scoped independently per user`() = runTest {
        repository.setEnabled("user-1", false)

        assertThat(repository.isEnabled("user-1")).isFalse()
        assertThat(repository.isEnabled("user-2")).isTrue()
    }
}
