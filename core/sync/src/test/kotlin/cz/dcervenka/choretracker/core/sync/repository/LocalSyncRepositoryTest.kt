package cz.dcervenka.choretracker.core.sync.repository

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.database.dao.ChoreDao
import cz.dcervenka.choretracker.core.database.dao.CompletionDao
import cz.dcervenka.choretracker.core.database.dao.CompletionParticipantDao
import cz.dcervenka.choretracker.core.database.dao.HouseholdDao
import cz.dcervenka.choretracker.core.database.dao.InviteDao
import cz.dcervenka.choretracker.core.database.dao.MemberDao
import cz.dcervenka.choretracker.core.database.dao.PendingSyncOperationDao
import cz.dcervenka.choretracker.core.database.dao.SyncStateDao
import cz.dcervenka.choretracker.core.database.entity.ChoreEntity
import cz.dcervenka.choretracker.core.database.entity.HouseholdEntity
import cz.dcervenka.choretracker.core.database.entity.InviteEntity
import cz.dcervenka.choretracker.core.database.entity.MemberEntity
import cz.dcervenka.choretracker.core.database.entity.PendingSyncOperationEntity
import cz.dcervenka.choretracker.core.model.auth.AppUser
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import cz.dcervenka.choretracker.core.model.sync.HouseholdSnapshot
import cz.dcervenka.choretracker.core.remote.contract.RemoteHouseholdDataSource
import cz.dcervenka.choretracker.core.test.mock.sampleChore
import cz.dcervenka.choretracker.core.test.mock.sampleHousehold
import cz.dcervenka.choretracker.core.test.mock.sampleInvite
import cz.dcervenka.choretracker.core.test.mock.sampleMembers
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

class LocalSyncRepositoryTest {

    @MockK
    lateinit var authRepository: AuthRepository

    @MockK
    lateinit var householdDao: HouseholdDao

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
    lateinit var syncStateDao: SyncStateDao

    @MockK
    lateinit var remoteHouseholdDataSource: RemoteHouseholdDataSource

    private val authState = MutableStateFlow<AuthState>(
        AuthState.Authenticated(
            user = AppUser(id = "user-1", email = "dana@example.com", displayName = "Dana"),
        ),
    )

    private lateinit var repository: LocalSyncRepository

    private val householdEntity = HouseholdEntity(
        id = "household-1",
        name = "Home",
        ownerUserId = "user-1",
        inviteCode = "ABC123",
        createdAt = Instant.parse("2026-01-01T10:00:00Z"),
    )

    private val memberEntity = MemberEntity(
        id = "member-1",
        householdId = "household-1",
        userId = "user-1",
        displayName = "Dana",
        role = HouseholdRole.OWNER.name,
        isCurrentUser = true,
    )

    private val choreEntity = ChoreEntity(
        id = "chore-1",
        householdId = "household-1",
        name = "Kitchen",
        isActive = true,
        createdAt = Instant.parse("2026-01-03T10:00:00Z"),
        deletedAt = null,
    )

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { authRepository.authState } returns authState
        coEvery { pendingSyncOperationDao.getAll() } returns emptyList()
        coEvery { syncStateDao.upsert(any()) } just Runs
        coEvery { householdDao.upsert(any()) } just Runs
        coEvery { memberDao.upsert(any()) } just Runs
        coEvery { choreDao.upsert(any()) } just Runs
        coEvery { completionDao.upsert(any()) } just Runs
        coEvery { completionParticipantDao.insertAll(any()) } just Runs
        coEvery { inviteDao.upsert(any()) } just Runs
        coEvery { memberDao.getMembers(any()) } returns emptyList()
        coEvery { inviteDao.getInvites(any()) } returns emptyList()
        coEvery { completionDao.getCompletions(any()) } returns emptyList()
        coEvery { memberDao.deleteById(any()) } just Runs
        coEvery { inviteDao.deleteById(any()) } just Runs
        coEvery { completionDao.deleteById(any()) } just Runs
        // ensureEmailSynced runs on first authenticated sync; return null so it exits early
        coEvery { householdDao.getCurrentHouseholdForUser(any()) } returns null
        repository = LocalSyncRepository(
            authRepository = authRepository,
            householdDao = householdDao,
            memberDao = memberDao,
            choreDao = choreDao,
            completionDao = completionDao,
            completionParticipantDao = completionParticipantDao,
            inviteDao = inviteDao,
            pendingSyncOperationDao = pendingSyncOperationDao,
            syncStateDao = syncStateDao,
            remoteHouseholdDataSource = remoteHouseholdDataSource,
        )
    }

    // syncPendingOperations

    @Test
    fun `syncPendingOperations skips when unauthenticated`() = runBlocking {
        authState.value = AuthState.SignedOut

        val result = repository.syncPendingOperations()

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        coVerify(exactly = 0) { pendingSyncOperationDao.getAll() }
    }

    @Test
    fun `syncPendingOperations skips when preview user`() = runBlocking {
        authState.value = AuthState.Authenticated(
            AppUser(id = "preview-user", email = null, displayName = "Preview User", isPreview = true),
        )

        val result = repository.syncPendingOperations()

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        coVerify(exactly = 0) { pendingSyncOperationDao.getAll() }
    }

    @Test
    fun `syncPendingOperations returns Success when no pending operations`() = runBlocking {
        coEvery { pendingSyncOperationDao.getAll() } returns emptyList()

        val result = repository.syncPendingOperations()

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        coVerify(exactly = 0) { remoteHouseholdDataSource.upsertHouseholdSnapshot(any(), any()) }
    }

    @Test
    fun `syncPendingOperations resolves householdId for chore entity type via dao lookup`() = runBlocking {
        val op = PendingSyncOperationEntity(
            id = "op-1",
            entityType = "chore",
            entityId = "chore-1",
            operationType = "upsert",
            payload = "Kitchen",
            createdAt = Instant.parse("2026-01-04T10:00:00Z"),
        )
        coEvery { pendingSyncOperationDao.getAll() } returns listOf(op)
        coEvery { choreDao.getChore("chore-1") } returns choreEntity
        coEvery { householdDao.getHousehold("household-1") } returns householdEntity
        coEvery { memberDao.findByUserId("household-1", "user-1") } returns memberEntity
        coEvery { completionParticipantDao.getParticipants("household-1") } returns emptyList()
        coEvery { choreDao.getChores("household-1") } returns listOf(choreEntity)
        coEvery { remoteHouseholdDataSource.upsertHouseholdSnapshot(any(), any()) } returns AppResult.Success(Unit)
        coEvery { pendingSyncOperationDao.delete(any()) } just Runs
        coEvery { completionDao.getCompletions("household-1") } returns emptyList()
        coEvery { inviteDao.getInvites("household-1") } returns emptyList()
        coEvery { memberDao.getMembers("household-1") } returns listOf(memberEntity)

        val result = repository.syncPendingOperations()

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        coVerify { remoteHouseholdDataSource.upsertHouseholdSnapshot(any(), "user-1") }
    }

    @Test
    fun `syncPendingOperations resolves householdId for completion delete from payload`() = runBlocking {
        val op = PendingSyncOperationEntity(
            id = "op-2",
            entityType = "completion",
            entityId = "completion-1",
            operationType = "delete",
            payload = "household-1",
            createdAt = Instant.parse("2026-01-04T10:00:00Z"),
        )
        coEvery { pendingSyncOperationDao.getAll() } returns listOf(op)
        coEvery { householdDao.getHousehold("household-1") } returns householdEntity
        coEvery { memberDao.findByUserId("household-1", "user-1") } returns memberEntity
        coEvery { completionParticipantDao.getParticipants("household-1") } returns emptyList()
        coEvery { choreDao.getChores("household-1") } returns emptyList()
        coEvery { completionDao.getCompletions("household-1") } returns emptyList()
        coEvery { inviteDao.getInvites("household-1") } returns emptyList()
        coEvery { memberDao.getMembers("household-1") } returns listOf(memberEntity)
        coEvery { remoteHouseholdDataSource.upsertHouseholdSnapshot(any(), any()) } returns AppResult.Success(Unit)
        coEvery { remoteHouseholdDataSource.deleteCompletion(any(), any()) } returns AppResult.Success(Unit)
        coEvery { pendingSyncOperationDao.delete(any()) } just Runs

        val result = repository.syncPendingOperations()

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        coVerify { remoteHouseholdDataSource.deleteCompletion("household-1", "completion-1") }
    }

    @Test
    fun `syncPendingOperations returns Error and records lastSyncAttemptAt when remote fails`() = runBlocking {
        val op = PendingSyncOperationEntity(
            id = "op-1",
            entityType = "chore",
            entityId = "chore-1",
            operationType = "upsert",
            payload = "Kitchen",
            createdAt = Instant.parse("2026-01-04T10:00:00Z"),
        )
        coEvery { pendingSyncOperationDao.getAll() } returns listOf(op)
        coEvery { choreDao.getChore("chore-1") } returns choreEntity
        coEvery { householdDao.getHousehold("household-1") } returns householdEntity
        coEvery { memberDao.findByUserId("household-1", "user-1") } returns memberEntity
        coEvery { completionParticipantDao.getParticipants("household-1") } returns emptyList()
        coEvery { choreDao.getChores("household-1") } returns emptyList()
        coEvery { completionDao.getCompletions("household-1") } returns emptyList()
        coEvery { inviteDao.getInvites("household-1") } returns emptyList()
        coEvery { memberDao.getMembers("household-1") } returns listOf(memberEntity)
        coEvery { remoteHouseholdDataSource.upsertHouseholdSnapshot(any(), any()) } returns
            AppResult.Error("Missing or insufficient permissions.")

        val result = repository.syncPendingOperations()

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        coVerify { syncStateDao.upsert(match { it.lastErrorMessage == "Missing or insufficient permissions." }) }
    }

    // restoreHouseholdForUser

    @Test
    fun `restoreHouseholdForUser returns Error when fetch fails`() = runBlocking {
        coEvery { remoteHouseholdDataSource.fetchHouseholdSnapshot("user-1") } returns
            AppResult.Error("Network error")

        val result = repository.restoreHouseholdForUser("user-1")

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        assertThat((result as AppResult.Error).message).isEqualTo("Network error")
    }

    @Test
    fun `restoreHouseholdForUser returns Success(false) when no remote snapshot`() = runBlocking {
        coEvery { remoteHouseholdDataSource.fetchHouseholdSnapshot("user-1") } returns AppResult.Success(null)

        val result = repository.restoreHouseholdForUser("user-1")

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat((result as AppResult.Success).value).isFalse()
        coVerify(exactly = 0) { householdDao.upsert(any()) }
    }

    @Test
    fun `restoreHouseholdForUser upserts all snapshot entities and returns Success(true)`() = runBlocking {
        val snapshot = buildSnapshot()
        coEvery { remoteHouseholdDataSource.fetchHouseholdSnapshot("user-1") } returns AppResult.Success(snapshot)

        val result = repository.restoreHouseholdForUser("user-1")

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat((result as AppResult.Success).value).isTrue()
        coVerify { householdDao.upsert(match { it.id == "household-1" }) }
        coVerify { memberDao.upsert(match { it.id == "member-1" }) }
        coVerify { choreDao.upsert(match { it.id == "chore-1" }) }
        coVerify { completionDao.upsert(match { it.id == "completion-1" }) }
        coVerify { inviteDao.upsert(match { it.id == "invite-1" }) }
    }

    @Test
    fun `restoreHouseholdForUser deduplicates members keeping userId-linked entry`() = runBlocking {
        val members = sampleMembers()
        val duplicateWithoutUserId = members[0].copy(userId = null)
        val snapshot = buildSnapshot(members = listOf(duplicateWithoutUserId, members[0], members[1]))
        coEvery { remoteHouseholdDataSource.fetchHouseholdSnapshot("user-1") } returns AppResult.Success(snapshot)
        coEvery { memberDao.getMembers("household-1") } returns emptyList()

        repository.restoreHouseholdForUser("user-1")

        coVerify(exactly = 1) { memberDao.upsert(match { it.id == "member-1" && it.userId == "user-1" }) }
    }

    @Test
    fun `restoreHouseholdForUser prunes members not in snapshot`() = runBlocking {
        val snapshot = buildSnapshot()
        val staleLocal = MemberEntity(
            id = "stale-member",
            householdId = "household-1",
            userId = null,
            displayName = "Gone",
            role = HouseholdRole.MEMBER.name,
            isCurrentUser = false,
        )
        coEvery { remoteHouseholdDataSource.fetchHouseholdSnapshot("user-1") } returns AppResult.Success(snapshot)
        coEvery { memberDao.getMembers("household-1") } returns listOf(memberEntity, staleLocal)

        repository.restoreHouseholdForUser("user-1")

        coVerify { memberDao.deleteById("stale-member") }
        coVerify(exactly = 0) { memberDao.deleteById("member-1") }
    }

    @Test
    fun `restoreHouseholdForUser deduplicates members preferring placeholder displayName`() = runBlocking {
        val claimed = sampleMembers()[0]
        val placeholder = claimed.copy(userId = null, displayName = "UserChosenName")
        val snapshot = buildSnapshot(members = listOf(claimed, placeholder))
        coEvery { remoteHouseholdDataSource.fetchHouseholdSnapshot("user-1") } returns AppResult.Success(snapshot)
        coEvery { memberDao.getMembers("household-1") } returns emptyList()

        repository.restoreHouseholdForUser("user-1")

        coVerify(exactly = 1) {
            memberDao.upsert(
                match { it.id == "member-1" && it.userId == "user-1" && it.displayName == "UserChosenName" },
            )
        }
    }

    @Test
    fun `syncPendingOperations calls markInviteConsumed with consumedByMemberId`() = runBlocking {
        val consumedAt = Instant.parse("2026-02-01T10:00:00Z")
        val consumedInvite = InviteEntity(
            id = "invite-1",
            householdId = "household-1",
            code = "ABC123",
            createdAt = Instant.parse("2026-01-01T10:00:00Z"),
            consumedAt = consumedAt,
            targetMemberId = null,
            consumedByMemberId = "member-1",
        )
        val op = PendingSyncOperationEntity(
            id = "op-1",
            entityType = "invite",
            entityId = "household-1",
            operationType = "consumed",
            payload = "invite-1",
            createdAt = Instant.parse("2026-01-04T10:00:00Z"),
        )
        coEvery { pendingSyncOperationDao.getAll() } returns listOf(op)
        coEvery { householdDao.getHousehold("household-1") } returns householdEntity
        coEvery { memberDao.findByUserId("household-1", "user-1") } returns memberEntity
        coEvery { completionParticipantDao.getParticipants("household-1") } returns emptyList()
        coEvery { choreDao.getChores("household-1") } returns emptyList()
        coEvery { completionDao.getCompletions("household-1") } returns emptyList()
        coEvery { inviteDao.getInvites("household-1") } returns listOf(consumedInvite)
        coEvery { memberDao.getMembers("household-1") } returns listOf(memberEntity)
        coEvery { remoteHouseholdDataSource.upsertHouseholdSnapshot(any(), any()) } returns AppResult.Success(Unit)
        coEvery { remoteHouseholdDataSource.markInviteConsumed(any(), any(), any(), any()) } returns AppResult.Success(Unit)
        coEvery { pendingSyncOperationDao.delete(any()) } just Runs

        val result = repository.syncPendingOperations()

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        coVerify { remoteHouseholdDataSource.markInviteConsumed("household-1", "invite-1", consumedAt, "member-1") }
    }
}

private fun buildSnapshot(
    members: List<cz.dcervenka.choretracker.core.model.household.HouseholdMember> = sampleMembers().take(1),
) = HouseholdSnapshot(
    household = sampleHousehold(),
    members = members,
    chores = listOf(sampleChore()),
    completions = listOf(
        cz.dcervenka.choretracker.core.model.chore.ChoreCompletion(
            id = "completion-1",
            householdId = "household-1",
            choreId = "chore-1",
            createdAt = Instant.parse("2026-03-30T10:00:00Z"),
            createdByUserId = "user-1",
            note = null,
            participantMemberIds = listOf("member-1"),
        ),
    ),
    invites = listOf(sampleInvite()),
)
