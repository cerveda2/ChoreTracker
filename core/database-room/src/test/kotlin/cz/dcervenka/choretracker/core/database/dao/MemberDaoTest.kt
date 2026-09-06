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
class MemberDaoTest {

    private lateinit var database: ChoreTrackerDatabase
    private lateinit var dao: MemberDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ChoreTrackerDatabase::class.java,
        ).build()
        dao = database.memberDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `observeMembers orders the current user first, then by displayName`() = runTest {
        dao.upsert(memberEntity("member-1", displayName = "Zebra", isCurrentUser = false))
        dao.upsert(memberEntity("member-2", displayName = "Mango", isCurrentUser = true))
        dao.upsert(memberEntity("member-3", displayName = "Apple", isCurrentUser = false))

        dao.observeMembers("household-1").test {
            assertThat(awaitItem().map { it.id }).isEqualTo(listOf("member-2", "member-3", "member-1"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getMembers orders by displayName only, unlike observeMembers`() = runTest {
        dao.upsert(memberEntity("member-1", displayName = "Zebra", isCurrentUser = false))
        dao.upsert(memberEntity("member-2", displayName = "Mango", isCurrentUser = true))

        val order = dao.getMembers("household-1").map { it.id }

        assertThat(order).isEqualTo(listOf("member-2", "member-1"))
    }

    @Test
    fun `findByUserId findCurrentUser and findById locate the right member`() = runTest {
        dao.upsert(memberEntity("member-1", userId = "user-1", isCurrentUser = true))
        dao.upsert(memberEntity("member-2", userId = "user-2", isCurrentUser = false))

        assertThat(dao.findByUserId("household-1", "user-2")?.id).isEqualTo("member-2")
        assertThat(dao.findCurrentUser("household-1")?.id).isEqualTo("member-1")
        assertThat(dao.findById("household-1", "member-2")?.id).isEqualTo("member-2")
        assertThat(dao.findByUserId("household-1", "missing")).isNull()
    }

    @Test
    fun `claimPlaceholder sets userId email isCurrentUser displayName and joinedViaInviteId`() = runTest {
        dao.upsert(
            memberEntity(
                "member-1",
                userId = null,
                displayName = "Placeholder",
                isCurrentUser = false,
                email = null,
                joinedViaInviteId = null,
            ),
        )

        dao.claimPlaceholder(
            memberId = "member-1",
            userId = "user-1",
            email = "user@example.com",
            displayName = "Real Name",
            inviteId = "invite-1",
        )

        val claimed = dao.findById("household-1", "member-1")
        assertThat(claimed?.userId).isEqualTo("user-1")
        assertThat(claimed?.email).isEqualTo("user@example.com")
        assertThat(claimed?.isCurrentUser).isTrue()
        assertThat(claimed?.displayName).isEqualTo("Real Name")
        assertThat(claimed?.joinedViaInviteId).isEqualTo("invite-1")
    }

    @Test
    fun `clearCurrentUser has no household scoping and clears every household's current user`() = runTest {
        dao.upsert(memberEntity("member-1", householdId = "household-1", isCurrentUser = true))
        dao.upsert(memberEntity("member-2", householdId = "household-2", isCurrentUser = true))

        dao.clearCurrentUser()

        assertThat(dao.findById("household-1", "member-1")?.isCurrentUser).isFalse()
        assertThat(dao.findById("household-2", "member-2")?.isCurrentUser).isFalse()
    }

    @Test
    fun `deleteById removes only the targeted member`() = runTest {
        dao.upsert(memberEntity("member-1"))
        dao.upsert(memberEntity("member-2"))

        dao.deleteById("member-1")

        assertThat(dao.findById("household-1", "member-1")).isNull()
        assertThat(dao.findById("household-1", "member-2")).isNotNull()
    }

    @Test
    fun `observeMembers re-emits after a subsequent upsert`() = runTest {
        dao.upsert(memberEntity("member-1", displayName = "Alpha"))

        dao.observeMembers("household-1").test {
            assertThat(awaitItem().map { it.id }).containsExactly("member-1")

            dao.upsert(memberEntity("member-2", displayName = "Beta"))

            assertThat(awaitItem().map { it.id }).containsExactly("member-1", "member-2")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
