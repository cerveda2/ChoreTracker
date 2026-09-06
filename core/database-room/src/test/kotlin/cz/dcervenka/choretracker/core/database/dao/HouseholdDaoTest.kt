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
class HouseholdDaoTest {

    private lateinit var database: ChoreTrackerDatabase
    private lateinit var dao: HouseholdDao
    private lateinit var memberDao: MemberDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ChoreTrackerDatabase::class.java,
        ).build()
        dao = database.householdDao()
        memberDao = database.memberDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `observeCurrentHousehold and getCurrentHousehold return the most recently created, with no user filter`() =
        runTest {
            dao.upsert(householdEntity("household-1", createdAt = instantAt(1)))
            dao.upsert(householdEntity("household-2", createdAt = instantAt(2)))

            assertThat(dao.getCurrentHousehold()?.id).isEqualTo("household-2")
            dao.observeCurrentHousehold().test {
                assertThat(awaitItem()?.id).isEqualTo("household-2")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeHouseholdForUser and getCurrentHouseholdForUser scope by the user's membership`() = runTest {
        dao.upsert(householdEntity("household-1"))
        dao.upsert(householdEntity("household-2"))
        memberDao.upsert(memberEntity("member-1", householdId = "household-1", userId = "user-1"))
        memberDao.upsert(memberEntity("member-2", householdId = "household-2", userId = "user-2"))

        assertThat(dao.getCurrentHouseholdForUser("user-1")?.id).isEqualTo("household-1")
        dao.observeHouseholdForUser("user-1").test {
            assertThat(awaitItem()?.id).isEqualTo("household-1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getHousehold and observeHousehold return null when missing`() = runTest {
        assertThat(dao.getHousehold("missing")).isNull()
        dao.observeHousehold("missing").test {
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateInviteCode only touches the targeted household`() = runTest {
        dao.upsert(householdEntity("household-1", inviteCode = "OLD1"))
        dao.upsert(householdEntity("household-2", inviteCode = "OLD2"))

        dao.updateInviteCode("household-1", "NEW1")

        assertThat(dao.getHousehold("household-1")?.inviteCode).isEqualTo("NEW1")
        assertThat(dao.getHousehold("household-2")?.inviteCode).isEqualTo("OLD2")
    }

    @Test
    fun `updateName only touches the targeted household`() = runTest {
        dao.upsert(householdEntity("household-1", name = "Old"))
        dao.upsert(householdEntity("household-2", name = "Untouched"))

        dao.updateName("household-1", "New")

        assertThat(dao.getHousehold("household-1")?.name).isEqualTo("New")
        assertThat(dao.getHousehold("household-2")?.name).isEqualTo("Untouched")
    }

    @Test
    fun `observeCurrentHousehold re-emits after a newer household is inserted`() = runTest {
        dao.upsert(householdEntity("household-1", createdAt = instantAt(1)))

        dao.observeCurrentHousehold().test {
            assertThat(awaitItem()?.id).isEqualTo("household-1")

            dao.upsert(householdEntity("household-2", createdAt = instantAt(2)))

            assertThat(awaitItem()?.id).isEqualTo("household-2")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
