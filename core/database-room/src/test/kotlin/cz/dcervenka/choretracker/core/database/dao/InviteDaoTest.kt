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
class InviteDaoTest {

    private lateinit var database: ChoreTrackerDatabase
    private lateinit var dao: InviteDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ChoreTrackerDatabase::class.java,
        ).build()
        dao = database.inviteDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `observeInvites and getInvites order by createdAt descending`() = runTest {
        dao.upsert(inviteEntity("invite-1", createdAt = instantAt(1)))
        dao.upsert(inviteEntity("invite-2", createdAt = instantAt(2)))

        assertThat(dao.getInvites("household-1").map { it.id }).isEqualTo(listOf("invite-2", "invite-1"))
    }

    @Test
    fun `findByCode returns the matching invite`() = runTest {
        dao.upsert(inviteEntity("invite-1", code = "ABC123"))

        assertThat(dao.findByCode("ABC123")?.id).isEqualTo("invite-1")
        assertThat(dao.findByCode("missing")).isNull()
    }

    @Test
    fun `findPendingByTargetMember excludes already-consumed invites`() = runTest {
        dao.upsert(
            inviteEntity("invite-1", targetMemberId = "member-1", consumedAt = instantAt(5)),
        )
        dao.upsert(
            inviteEntity("invite-2", targetMemberId = "member-1", consumedAt = null),
        )

        val pending = dao.findPendingByTargetMember("household-1", "member-1")

        assertThat(pending?.id).isEqualTo("invite-2")
    }

    @Test
    fun `markConsumed stamps consumedAt and consumedByMemberId`() = runTest {
        dao.upsert(inviteEntity("invite-1", consumedAt = null))

        dao.markConsumed("invite-1", instantAt(5), "member-1")

        val updated = dao.findByCode(dao.getInvites("household-1").first().code)
        assertThat(updated?.consumedAt).isEqualTo(instantAt(5))
        assertThat(updated?.consumedByMemberId).isEqualTo("member-1")
    }

    @Test
    fun `updateTargetMemberId only touches the targeted invite`() = runTest {
        dao.upsert(inviteEntity("invite-1", targetMemberId = null))
        dao.upsert(inviteEntity("invite-2", targetMemberId = null))

        dao.updateTargetMemberId("invite-1", "member-1")

        assertThat(dao.getInvites("household-1").first { it.id == "invite-1" }.targetMemberId).isEqualTo("member-1")
        assertThat(dao.getInvites("household-1").first { it.id == "invite-2" }.targetMemberId).isNull()
    }

    @Test
    fun `deleteById removes only the targeted invite`() = runTest {
        dao.upsert(inviteEntity("invite-1"))
        dao.upsert(inviteEntity("invite-2"))

        dao.deleteById("invite-1")

        assertThat(dao.getInvites("household-1").map { it.id }).containsExactly("invite-2")
    }
}
