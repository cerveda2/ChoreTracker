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
class ChoreDaoTest {

    private lateinit var database: ChoreTrackerDatabase
    private lateinit var dao: ChoreDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ChoreTrackerDatabase::class.java,
        ).build()
        dao = database.choreDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `observeChores and getChores order active chores first then by name`() = runTest {
        dao.upsert(choreEntity("chore-1", name = "Zebra", isActive = true))
        dao.upsert(choreEntity("chore-2", name = "Apple", isActive = false))
        dao.upsert(choreEntity("chore-3", name = "Mango", isActive = true))

        val expectedOrder = listOf("chore-3", "chore-1", "chore-2")
        assertThat(dao.getChores("household-1").map { it.id }).isEqualTo(expectedOrder)
        dao.observeChores("household-1").test {
            assertThat(awaitItem().map { it.id }).isEqualTo(expectedOrder)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getChore returns null when missing`() = runTest {
        assertThat(dao.getChore("missing")).isNull()
    }

    @Test
    fun `upsert replaces an existing chore by id`() = runTest {
        dao.upsert(choreEntity("chore-1", name = "Original"))

        dao.upsert(choreEntity("chore-1", name = "Replaced"))

        assertThat(dao.getChore("chore-1")?.name).isEqualTo("Replaced")
        assertThat(dao.getChores("household-1")).hasSize(1)
    }

    @Test
    fun `markDeleted sets isActive false and stamps deletedAt`() = runTest {
        dao.upsert(choreEntity("chore-1", isActive = true, deletedAt = null))

        dao.markDeleted("chore-1", instantAt(99))

        val updated = dao.getChore("chore-1")
        assertThat(updated?.isActive).isFalse()
        assertThat(updated?.deletedAt).isEqualTo(instantAt(99))
    }

    @Test
    fun `updateActive only touches the targeted chore`() = runTest {
        dao.upsert(choreEntity("chore-1", isActive = true))
        dao.upsert(choreEntity("chore-2", isActive = true))

        dao.updateActive("chore-1", false)

        assertThat(dao.getChore("chore-1")?.isActive).isFalse()
        assertThat(dao.getChore("chore-2")?.isActive).isTrue()
    }

    @Test
    fun `updateName only touches the targeted chore`() = runTest {
        dao.upsert(choreEntity("chore-1", name = "Old"))
        dao.upsert(choreEntity("chore-2", name = "Untouched"))

        dao.updateName("chore-1", "New")

        assertThat(dao.getChore("chore-1")?.name).isEqualTo("New")
        assertThat(dao.getChore("chore-2")?.name).isEqualTo("Untouched")
    }

    @Test
    fun `updateFrequencyDays can set a value and clear it back to null`() = runTest {
        dao.upsert(choreEntity("chore-1", frequencyDays = 7))

        dao.updateFrequencyDays("chore-1", 14)
        assertThat(dao.getChore("chore-1")?.frequencyDays).isEqualTo(14)

        dao.updateFrequencyDays("chore-1", null)
        assertThat(dao.getChore("chore-1")?.frequencyDays).isNull()
    }

    @Test
    fun `updateCategory only touches the targeted chore`() = runTest {
        dao.upsert(choreEntity("chore-1", category = "OTHER"))
        dao.upsert(choreEntity("chore-2", category = "OTHER"))

        dao.updateCategory("chore-1", "KITCHEN")

        assertThat(dao.getChore("chore-1")?.category).isEqualTo("KITCHEN")
        assertThat(dao.getChore("chore-2")?.category).isEqualTo("OTHER")
    }

    @Test
    fun `observeChores re-emits after a subsequent upsert`() = runTest {
        dao.upsert(choreEntity("chore-1"))

        dao.observeChores("household-1").test {
            assertThat(awaitItem().map { it.id }).containsExactly("chore-1")

            dao.upsert(choreEntity("chore-2"))

            assertThat(awaitItem().map { it.id }).containsExactly("chore-1", "chore-2")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
