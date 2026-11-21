package cz.dcervenka.choretracker.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreUpdatePromptRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repository: DataStoreUpdatePromptRepository

    @Before
    fun setUp() {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()),
            produceFile = { tempFolder.newFile("test.preferences_pb") },
        )
        repository = DataStoreUpdatePromptRepository(dataStore)
    }

    @Test
    fun `remembers the last offered version`() = runTest {
        assertThat(repository.getLastOfferedVersion()).isNull()

        repository.setLastOfferedVersion(42)

        assertThat(repository.getLastOfferedVersion()).isEqualTo(42)
    }
}
