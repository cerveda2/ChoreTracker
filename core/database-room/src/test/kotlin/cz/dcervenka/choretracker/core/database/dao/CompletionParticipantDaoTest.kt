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
class CompletionParticipantDaoTest {

    private lateinit var database: ChoreTrackerDatabase
    private lateinit var completionDao: CompletionDao
    private lateinit var dao: CompletionParticipantDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ChoreTrackerDatabase::class.java,
        ).build()
        completionDao = database.completionDao()
        dao = database.completionParticipantDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `observeParticipants and getParticipants scope to the household via the completion join`() = runTest {
        completionDao.upsert(completionEntity("completion-1", householdId = "household-1"))
        completionDao.upsert(completionEntity("completion-2", householdId = "household-2"))
        dao.insertAll(
            listOf(
                completionParticipantEntity("completion-1", "member-1"),
                completionParticipantEntity("completion-2", "member-2"),
            ),
        )

        assertThat(dao.getParticipants("household-1")).containsExactly(
            completionParticipantEntity("completion-1", "member-1"),
        )
        dao.observeParticipants("household-1").test {
            assertThat(awaitItem()).containsExactly(completionParticipantEntity("completion-1", "member-1"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `insertAll inserts every participant in the batch`() = runTest {
        completionDao.upsert(completionEntity("completion-1"))

        dao.insertAll(
            listOf(
                completionParticipantEntity("completion-1", "member-1"),
                completionParticipantEntity("completion-1", "member-2"),
            ),
        )

        assertThat(dao.getParticipants("household-1")).hasSize(2)
    }

    @Test
    fun `deleteByCompletionId removes only that completion's participants`() = runTest {
        completionDao.upsert(completionEntity("completion-1"))
        completionDao.upsert(completionEntity("completion-2"))
        dao.insertAll(
            listOf(
                completionParticipantEntity("completion-1", "member-1"),
                completionParticipantEntity("completion-2", "member-2"),
            ),
        )

        dao.deleteByCompletionId("completion-1")

        assertThat(dao.getParticipants("household-1").map { it.completionId }).containsExactly("completion-2")
    }

    @Test
    fun `deleting the completion cascades and removes its participants`() = runTest {
        completionDao.upsert(completionEntity("completion-1"))
        dao.insertAll(listOf(completionParticipantEntity("completion-1", "member-1")))

        completionDao.deleteById("completion-1")

        assertThat(dao.getParticipants("household-1")).isEmpty()
    }
}
