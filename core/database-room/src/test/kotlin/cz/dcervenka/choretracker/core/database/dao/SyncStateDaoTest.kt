package cz.dcervenka.choretracker.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.database.database.ChoreTrackerDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric 4.16.1's newest supported SDK is 36; targetSdk is 37, ahead of what
// Robolectric ships shadows for yet. Pin the test SDK explicitly until Robolectric catches up.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyncStateDaoTest {

    private lateinit var database: ChoreTrackerDatabase
    private lateinit var dao: SyncStateDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ChoreTrackerDatabase::class.java,
        ).build()
        dao = database.syncStateDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `observeSyncState scopes by household and returns null when missing`() = runTest {
        dao.upsert(syncStateEntity("household-1", pendingOperations = 3))
        dao.upsert(syncStateEntity("household-2", pendingOperations = 0))

        dao.observeSyncState("household-1").test {
            assertThat(awaitItem()?.pendingOperations).isEqualTo(3)
            cancelAndIgnoreRemainingEvents()
        }
        dao.observeSyncState("missing").test {
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `upsert replaces the existing row for that householdId`() = runTest {
        dao.upsert(syncStateEntity("household-1", pendingOperations = 1, lastErrorMessage = "boom"))

        dao.upsert(syncStateEntity("household-1", pendingOperations = 0, lastErrorMessage = null))

        dao.observeSyncState("household-1").test {
            val state = awaitItem()
            assertThat(state?.pendingOperations).isEqualTo(0)
            assertThat(state?.lastErrorMessage).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeSyncState re-emits after a subsequent upsert`() = runTest {
        dao.upsert(syncStateEntity("household-1", pendingOperations = 1))

        dao.observeSyncState("household-1").test {
            assertThat(awaitItem()?.pendingOperations).isEqualTo(1)

            dao.upsert(syncStateEntity("household-1", pendingOperations = 2))

            assertThat(awaitItem()?.pendingOperations).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
