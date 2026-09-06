package cz.dcervenka.choretracker.core.sync.repository

import cz.dcervenka.choretracker.core.database.dao.ChoreDao
import cz.dcervenka.choretracker.core.database.dao.CompletionDao
import cz.dcervenka.choretracker.core.database.dao.CompletionParticipantDao
import cz.dcervenka.choretracker.core.database.dao.InviteDao
import cz.dcervenka.choretracker.core.database.dao.MemberDao
import cz.dcervenka.choretracker.core.database.dao.PendingSyncOperationDao
import cz.dcervenka.choretracker.core.database.database.ChoreTrackerDatabase
import cz.dcervenka.choretracker.core.database.entity.CompletionEntity
import cz.dcervenka.choretracker.core.database.entity.InviteEntity
import cz.dcervenka.choretracker.core.database.entity.MemberEntity
import cz.dcervenka.choretracker.core.database.entity.PendingSyncOperationEntity
import cz.dcervenka.choretracker.core.model.chore.ChoreCompletion
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import cz.dcervenka.choretracker.core.test.mock.sampleChore
import cz.dcervenka.choretracker.core.test.mock.sampleInvite
import cz.dcervenka.choretracker.core.test.mock.sampleMembers
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.just
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

class RealtimeSyncApplierTest {

    @MockK
    lateinit var memberDao: MemberDao

    @MockK
    lateinit var choreDao: ChoreDao

    @MockK
    lateinit var completionDao: CompletionDao

    @MockK
    lateinit var completionParticipantDao: CompletionParticipantDao

    @MockK
    lateinit var inviteDao: InviteDao

    @MockK
    lateinit var pendingSyncOperationDao: PendingSyncOperationDao

    @MockK
    lateinit var database: ChoreTrackerDatabase

    private lateinit var applier: RealtimeSyncApplier

    private val memberEntity = MemberEntity(
        id = "member-1",
        householdId = "household-1",
        userId = "user-1",
        displayName = "Dana",
        role = HouseholdRole.OWNER.name,
        isCurrentUser = true,
    )

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        coEvery { memberDao.upsert(any()) } just Runs
        coEvery { memberDao.getMembers(any()) } returns emptyList()
        coEvery { memberDao.deleteById(any()) } just Runs
        coEvery { choreDao.upsert(any()) } just Runs
        coEvery { completionDao.upsert(any()) } just Runs
        coEvery { completionDao.getCompletions(any()) } returns emptyList()
        coEvery { completionDao.deleteById(any()) } just Runs
        coEvery { completionParticipantDao.insertAll(any()) } just Runs
        coEvery { completionParticipantDao.deleteByCompletionId(any()) } just Runs
        coEvery { inviteDao.upsert(any()) } just Runs
        coEvery { inviteDao.getInvites(any()) } returns emptyList()
        coEvery { inviteDao.deleteById(any()) } just Runs
        coEvery { pendingSyncOperationDao.getAll() } returns emptyList()
        coEvery { database.clearAll() } just Runs
        applier = RealtimeSyncApplier(
            memberDao = memberDao,
            choreDao = choreDao,
            completionDao = completionDao,
            completionParticipantDao = completionParticipantDao,
            inviteDao = inviteDao,
            pendingSyncOperationDao = pendingSyncOperationDao,
            database = database,
        )
    }

    @Test
    fun `applyMembers upserts members received from the remote listener`() = runBlocking {
        applier.applyMembers("household-1", "user-1", sampleMembers())

        coVerify { memberDao.upsert(match { it.id == "member-1" }) }
        coVerify { memberDao.upsert(match { it.id == "member-2" }) }
    }

    @Test
    fun `applyMembers prunes members no longer present in the remote listener`() = runBlocking {
        val staleMember = MemberEntity(
            id = "stale-member",
            householdId = "household-1",
            userId = null,
            displayName = "Gone",
            role = HouseholdRole.MEMBER.name,
            isCurrentUser = false,
        )
        coEvery { memberDao.getMembers("household-1") } returns listOf(memberEntity, staleMember)

        applier.applyMembers("household-1", "user-1", listOf(sampleMembers()[0]))

        coVerify { memberDao.deleteById("stale-member") }
        coVerify(exactly = 0) { memberDao.deleteById("member-1") }
    }

    @Test
    fun `applyMembers skips pruning when the household has a pending member operation`() = runBlocking {
        val staleMember = MemberEntity(
            id = "stale-member",
            householdId = "household-1",
            userId = null,
            displayName = "Gone",
            role = HouseholdRole.MEMBER.name,
            isCurrentUser = false,
        )
        coEvery { memberDao.getMembers("household-1") } returns listOf(memberEntity, staleMember)
        coEvery { pendingSyncOperationDao.getAll() } returns listOf(
            PendingSyncOperationEntity(
                id = "op-1",
                entityType = "member",
                entityId = "household-1",
                operationType = "upsert",
                payload = "New Member",
                createdAt = Instant.parse("2026-03-30T10:00:00Z"),
            ),
        )

        applier.applyMembers("household-1", "user-1", listOf(sampleMembers()[0]))

        coVerify(exactly = 0) { memberDao.deleteById(any()) }
    }

    @Test
    fun `applyMembers clears local data when current user missing from remote members`() = runBlocking {
        applier.applyMembers("household-1", "user-1", listOf(sampleMembers()[1]))

        coVerify { database.clearAll() }
        coVerify(exactly = 0) { memberDao.upsert(any()) }
    }

    @Test
    fun `applyCompletions upserts completions and replaces their participants`() = runBlocking {
        val completion = ChoreCompletion(
            id = "completion-1",
            householdId = "household-1",
            choreId = "chore-1",
            createdAt = Instant.parse("2026-03-30T10:00:00Z"),
            createdByUserId = "user-1",
            note = null,
            participantMemberIds = listOf("member-1", "member-2"),
        )

        applier.applyCompletions("household-1", listOf(completion))

        coVerify { completionDao.upsert(match { it.id == "completion-1" }) }
        coVerify { completionParticipantDao.deleteByCompletionId("completion-1") }
        coVerify {
            completionParticipantDao.insertAll(
                match { participants -> participants.map { it.memberId }.toSet() == setOf("member-1", "member-2") },
            )
        }
    }

    @Test
    fun `applyCompletions prunes completions no longer present in the remote listener`() = runBlocking {
        coEvery { completionDao.getCompletions("household-1") } returns listOf(
            CompletionEntity(
                id = "stale-completion",
                householdId = "household-1",
                choreId = "chore-1",
                createdAt = Instant.parse("2026-03-30T10:00:00Z"),
                createdByUserId = "user-1",
                note = null,
            ),
        )

        applier.applyCompletions("household-1", emptyList())

        coVerify { completionDao.deleteById("stale-completion") }
    }

    @Test
    fun `applyCompletions does not prune a completion with a pending sync operation`() = runBlocking {
        coEvery { completionDao.getCompletions("household-1") } returns listOf(
            CompletionEntity(
                id = "unsynced-completion",
                householdId = "household-1",
                choreId = "chore-1",
                createdAt = Instant.parse("2026-03-30T10:00:00Z"),
                createdByUserId = "user-1",
                note = null,
            ),
        )
        coEvery { pendingSyncOperationDao.getAll() } returns listOf(
            PendingSyncOperationEntity(
                id = "op-1",
                entityType = "completion",
                entityId = "unsynced-completion",
                operationType = "upsert",
                payload = "",
                createdAt = Instant.parse("2026-03-30T10:00:00Z"),
            ),
        )

        applier.applyCompletions("household-1", emptyList())

        coVerify(exactly = 0) { completionDao.deleteById("unsynced-completion") }
    }

    @Test
    fun `applyInvites upserts invites received from the remote listener`() = runBlocking {
        val invite = sampleInvite()

        applier.applyInvites("household-1", listOf(invite))

        coVerify { inviteDao.upsert(match { it.id == invite.id && it.consumedAt == invite.consumedAt }) }
    }

    @Test
    fun `applyInvites prunes invites no longer present in the remote listener`() = runBlocking {
        coEvery { inviteDao.getInvites("household-1") } returns listOf(
            InviteEntity(
                id = "stale-invite",
                householdId = "household-1",
                code = "STALE123",
                createdAt = Instant.parse("2026-03-30T10:00:00Z"),
                consumedAt = null,
            ),
        )

        applier.applyInvites("household-1", emptyList())

        coVerify { inviteDao.deleteById("stale-invite") }
    }

    @Test
    fun `applyInvites skips pruning when the household has a pending invite operation`() = runBlocking {
        coEvery { inviteDao.getInvites("household-1") } returns listOf(
            InviteEntity(
                id = "unsynced-invite",
                householdId = "household-1",
                code = "NEW12345",
                createdAt = Instant.parse("2026-03-30T10:00:00Z"),
                consumedAt = null,
            ),
        )
        coEvery { pendingSyncOperationDao.getAll() } returns listOf(
            PendingSyncOperationEntity(
                id = "op-1",
                entityType = "invite",
                entityId = "household-1",
                operationType = "upsert",
                payload = "NEW12345",
                createdAt = Instant.parse("2026-03-30T10:00:00Z"),
            ),
        )

        applier.applyInvites("household-1", emptyList())

        coVerify(exactly = 0) { inviteDao.deleteById(any()) }
    }

    @Test
    fun `applyChores upserts chores received from the remote listener`() = runBlocking {
        val chore = sampleChore()

        applier.applyChores(listOf(chore))

        coVerify { choreDao.upsert(match { it.id == chore.id && it.name == chore.name }) }
    }
}
