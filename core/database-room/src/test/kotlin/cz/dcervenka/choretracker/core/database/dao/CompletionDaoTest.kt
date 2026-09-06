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
class CompletionDaoTest {

    private lateinit var database: ChoreTrackerDatabase
    private lateinit var dao: CompletionDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ChoreTrackerDatabase::class.java,
        ).build()
        dao = database.completionDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `observeCompletions and getCompletions order by createdAt descending`() = runTest {
        dao.upsert(completionEntity("completion-1", createdAt = instantAt(1)))
        dao.upsert(completionEntity("completion-2", createdAt = instantAt(3)))
        dao.upsert(completionEntity("completion-3", createdAt = instantAt(2)))

        val expectedOrder = listOf("completion-2", "completion-3", "completion-1")
        assertThat(dao.getCompletions("household-1").map { it.id }).isEqualTo(expectedOrder)
        dao.observeCompletions("household-1").test {
            assertThat(awaitItem().map { it.id }).isEqualTo(expectedOrder)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeRecentCompletions respects the limit`() = runTest {
        dao.upsert(completionEntity("completion-1", createdAt = instantAt(1)))
        dao.upsert(completionEntity("completion-2", createdAt = instantAt(2)))
        dao.upsert(completionEntity("completion-3", createdAt = instantAt(3)))

        dao.observeRecentCompletions("household-1", limit = 2).test {
            assertThat(awaitItem().map { it.id }).isEqualTo(listOf("completion-3", "completion-2"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeCompletionsByChore filters to the given chore`() = runTest {
        dao.upsert(completionEntity("completion-1", choreId = "chore-1"))
        dao.upsert(completionEntity("completion-2", choreId = "chore-2"))

        dao.observeCompletionsByChore("household-1", "chore-1").test {
            assertThat(awaitItem().map { it.id }).containsExactly("completion-1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getCompletion returns null when missing`() = runTest {
        assertThat(dao.getCompletion("missing")).isNull()
    }

    @Test
    fun `upsert replaces an existing completion by id`() = runTest {
        dao.upsert(completionEntity("completion-1", note = "Original"))

        dao.upsert(completionEntity("completion-1", note = "Replaced"))

        assertThat(dao.getCompletion("completion-1")?.note).isEqualTo("Replaced")
        assertThat(dao.getCompletions("household-1")).hasSize(1)
    }

    @Test
    fun `deleteById removes only the targeted completion`() = runTest {
        dao.upsert(completionEntity("completion-1"))
        dao.upsert(completionEntity("completion-2"))

        dao.deleteById("completion-1")

        assertThat(dao.getCompletion("completion-1")).isNull()
        assertThat(dao.getCompletion("completion-2")).isNotNull()
    }

    @Test
    fun `observeCompletions re-emits after a subsequent upsert`() = runTest {
        dao.upsert(completionEntity("completion-1", createdAt = instantAt(1)))

        dao.observeCompletions("household-1").test {
            assertThat(awaitItem().map { it.id }).containsExactly("completion-1")

            dao.upsert(completionEntity("completion-2", createdAt = instantAt(2)))

            assertThat(awaitItem().map { it.id }).containsExactly("completion-2", "completion-1")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
