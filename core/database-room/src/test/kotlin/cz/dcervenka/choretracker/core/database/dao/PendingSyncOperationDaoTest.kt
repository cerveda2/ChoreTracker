package cz.dcervenka.choretracker.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
class PendingSyncOperationDaoTest {

    private lateinit var database: ChoreTrackerDatabase
    private lateinit var dao: PendingSyncOperationDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ChoreTrackerDatabase::class.java,
        ).build()
        dao = database.pendingSyncOperationDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `getAll orders by createdAt and has no per-household or per-entity scoping`() = runTest {
        dao.upsert(pendingSyncOperationEntity("op-1", entityId = "entity-a", createdAt = instantAt(2)))
        dao.upsert(pendingSyncOperationEntity("op-2", entityId = "entity-b", createdAt = instantAt(1)))

        val all = dao.getAll()

        assertThat(all.map { it.id }).isEqualTo(listOf("op-2", "op-1"))
    }

    @Test
    fun `upsert replaces an existing operation by id`() = runTest {
        dao.upsert(pendingSyncOperationEntity("op-1", payload = "original"))

        dao.upsert(pendingSyncOperationEntity("op-1", payload = "replaced"))

        assertThat(dao.getAll()).hasSize(1)
        assertThat(dao.getAll().first().payload).isEqualTo("replaced")
    }

    @Test
    fun `delete removes only the targeted operation`() = runTest {
        dao.upsert(pendingSyncOperationEntity("op-1"))
        dao.upsert(pendingSyncOperationEntity("op-2"))

        dao.delete("op-1")

        assertThat(dao.getAll().map { it.id }).containsExactly("op-2")
    }

    @Test
    fun `deleteByEntityId removes every operation for that entity`() = runTest {
        dao.upsert(pendingSyncOperationEntity("op-1", entityId = "entity-a"))
        dao.upsert(pendingSyncOperationEntity("op-2", entityId = "entity-a"))
        dao.upsert(pendingSyncOperationEntity("op-3", entityId = "entity-b"))

        dao.deleteByEntityId("entity-a")

        assertThat(dao.getAll().map { it.id }).containsExactly("op-3")
    }

    @Test
    fun `pendingCount reflects the current row count`() = runTest {
        assertThat(dao.pendingCount()).isEqualTo(0)

        dao.upsert(pendingSyncOperationEntity("op-1"))
        dao.upsert(pendingSyncOperationEntity("op-2"))

        assertThat(dao.pendingCount()).isEqualTo(2)
    }
}
