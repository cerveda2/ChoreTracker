package cz.dcervenka.choretracker.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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

class DataStoreNotificationsBannerRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repository: DataStoreNotificationsBannerRepository

    @Before
    fun setUp() {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()),
            produceFile = { tempFolder.newFile("test.preferences_pb") },
        )
        repository = DataStoreNotificationsBannerRepository(dataStore)
    }

    @Test
    fun `banner is not dismissed until dismiss is called`() = runTest {
        assertThat(repository.observeDismissed().first()).isFalse()

        repository.dismiss()

        assertThat(repository.observeDismissed().first()).isTrue()
    }
}
