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
import cz.dcervenka.choretracker.core.database.database.ChoreTrackerDatabase
import cz.dcervenka.choretracker.core.database.entity.ChoreEntity
import cz.dcervenka.choretracker.core.database.entity.CompletionEntity
import cz.dcervenka.choretracker.core.database.entity.HouseholdEntity
import cz.dcervenka.choretracker.core.database.entity.InviteEntity
import cz.dcervenka.choretracker.core.database.entity.MemberEntity
import cz.dcervenka.choretracker.core.database.entity.PendingSyncOperationEntity
import cz.dcervenka.choretracker.core.model.auth.AppUser
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.model.chore.ChoreCompletion
import cz.dcervenka.choretracker.core.model.household.Household
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import cz.dcervenka.choretracker.core.model.sync.HouseholdSnapshot
import cz.dcervenka.choretracker.core.remote.contract.RemoteHouseholdDataSource
import cz.dcervenka.choretracker.core.test.mock.sampleChore
import cz.dcervenka.choretracker.core.test.mock.sampleHousehold
import cz.dcervenka.choretracker.core.test.mock.sampleInvite
import cz.dcervenka.choretracker.core.test.mock.sampleMembers
import cz.dcervenka.choretracker.core.test.rule.TestCoroutineRule
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

class LocalSyncRepositoryTest {

    @get:Rule
    val coroutineRule = TestCoroutineRule(startPaused = true)

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

    @MockK
    lateinit var database: ChoreTrackerDatabase

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
        coEvery { completionParticipantDao.deleteByCompletionId(any()) } just Runs
        every { remoteHouseholdDataSource.observeMembers(any(), any()) } returns MutableStateFlow(emptyList())
        every { remoteHouseholdDataSource.observeCompletions(any()) } returns MutableStateFlow(emptyList())
        every { remoteHouseholdDataSource.observeInvites(any()) } returns MutableStateFlow(emptyList())
        every { remoteHouseholdDataSource.observeChores(any()) } returns MutableStateFlow(emptyList())
        // ensureEmailSynced runs on first authenticated sync; return null so it exits early
        coEvery { householdDao.getCurrentHouseholdForUser(any()) } returns null
        // Real-time subscription init block — paused dispatcher means this never runs unless a
        // test explicitly advances it (see LocalSyncRepositoryRealtimeTest); default to a no-op
        // household so it stays inert for tests that don't care about it.
        every { householdDao.observeHouseholdForUser(any()) } returns MutableStateFlow(null)
        coEvery { database.clearAll() } just Runs
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
            database = database,
            syncScope = CoroutineScope(SupervisorJob() + coroutineRule.dispatcher),
        )
    }

    // syncPendingOperations

    @Test
    fun `syncPendingOperations skips when unauthenticated`() = runTest(coroutineRule.dispatcher) {
        authState.value = AuthState.SignedOut

        val resultDeferred = async { repository.syncPendingOperations() }
        advanceUntilIdle()
        val result = resultDeferred.await()

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        coVerify(exactly = 0) { pendingSyncOperationDao.getAll() }
    }

    @Test
    fun `syncPendingOperations skips when preview user`() = runTest(coroutineRule.dispatcher) {
        authState.value = AuthState.Authenticated(
            AppUser(id = "preview-user", email = null, displayName = "Preview User", isPreview = true),
        )

        val resultDeferred = async { repository.syncPendingOperations() }
        advanceUntilIdle()
        val result = resultDeferred.await()

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        coVerify(exactly = 0) { pendingSyncOperationDao.getAll() }
    }

    @Test
    fun `syncPendingOperations returns Success when no pending operations`() = runTest(coroutineRule.dispatcher) {
        coEvery { pendingSyncOperationDao.getAll() } returns emptyList()

        val resultDeferred = async { repository.syncPendingOperations() }
        advanceUntilIdle()
        val result = resultDeferred.await()

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        coVerify(exactly = 0) { remoteHouseholdDataSource.upsertHouseholdSnapshot(any(), any()) }
    }

    @Test
    fun `syncPendingOperations discards pending operations when the local household is gone`() =
        runTest(coroutineRule.dispatcher) {
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
            coEvery { householdDao.getHousehold("household-1") } returns null
            coEvery { memberDao.findByUserId("household-1", "user-1") } returns null
            coEvery { pendingSyncOperationDao.delete(any()) } just Runs

            val resultDeferred = async { repository.syncPendingOperations() }
            advanceUntilIdle()
            val result = resultDeferred.await()

            assertThat(result).isInstanceOf(AppResult.Success::class.java)
            coVerify(exactly = 1) { pendingSyncOperationDao.delete("op-1") }
            coVerify(exactly = 0) { remoteHouseholdDataSource.upsertHouseholdSnapshot(any(), any()) }
            coVerify(exactly = 0) { remoteHouseholdDataSource.upsertMemberSnapshot(any(), any(), any(), any()) }
        }

    @Test
    fun `syncPendingOperations resolves householdId for chore entity type via dao lookup`() =
        runTest(coroutineRule.dispatcher) {
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

            val resultDeferred = async { repository.syncPendingOperations() }
            advanceUntilIdle()
            val result = resultDeferred.await()

            assertThat(result).isInstanceOf(AppResult.Success::class.java)
            coVerify { remoteHouseholdDataSource.upsertHouseholdSnapshot(any(), "user-1") }
        }

    @Test
    fun `syncPendingOperations includes joinedViaInviteId in the owner's pushed snapshot`() =
        runTest(coroutineRule.dispatcher) {
            val op = PendingSyncOperationEntity(
                id = "op-1",
                entityType = "member",
                entityId = "household-1",
                operationType = "join",
                payload = "user-1",
                createdAt = Instant.parse("2026-01-04T10:00:00Z"),
            )
            val joinedMember = memberEntity.copy(joinedViaInviteId = "invite-1")
            coEvery { pendingSyncOperationDao.getAll() } returns listOf(op)
            coEvery { householdDao.getHousehold("household-1") } returns householdEntity
            coEvery { memberDao.findByUserId("household-1", "user-1") } returns memberEntity
            coEvery { completionParticipantDao.getParticipants("household-1") } returns emptyList()
            coEvery { choreDao.getChores("household-1") } returns emptyList()
            coEvery { completionDao.getCompletions("household-1") } returns emptyList()
            coEvery { inviteDao.getInvites("household-1") } returns emptyList()
            coEvery { memberDao.getMembers("household-1") } returns listOf(joinedMember)
            coEvery { remoteHouseholdDataSource.upsertHouseholdSnapshot(any(), any()) } returns AppResult.Success(Unit)
            coEvery { pendingSyncOperationDao.delete(any()) } just Runs

            val resultDeferred = async { repository.syncPendingOperations() }
            advanceUntilIdle()
            val result = resultDeferred.await()

            assertThat(result).isInstanceOf(AppResult.Success::class.java)
            coVerify {
                remoteHouseholdDataSource.upsertHouseholdSnapshot(
                    match { snapshot -> snapshot.members.any { it.joinedViaInviteId == "invite-1" } },
                    "user-1",
                )
            }
        }

    @Test
    fun `syncPendingOperations pushes joinedViaInviteId when a non-owner member syncs their own join`() =
        runTest(coroutineRule.dispatcher) {
            authState.value = AuthState.Authenticated(
                AppUser(id = "user-2", email = "joiner@example.com", displayName = "Joiner"),
            )
            val op = PendingSyncOperationEntity(
                id = "op-1",
                entityType = "member",
                entityId = "household-1",
                operationType = "join",
                payload = "user-2",
                createdAt = Instant.parse("2026-01-04T10:00:00Z"),
            )
            val joinedMember = MemberEntity(
                id = "member-2",
                householdId = "household-1",
                userId = "user-2",
                displayName = "Joiner",
                role = HouseholdRole.MEMBER.name,
                isCurrentUser = true,
                joinedViaInviteId = "invite-1",
            )
            coEvery { pendingSyncOperationDao.getAll() } returns listOf(op)
            coEvery { householdDao.getHousehold("household-1") } returns householdEntity
            coEvery { memberDao.findByUserId("household-1", "user-2") } returns joinedMember
            coEvery { completionParticipantDao.getParticipants("household-1") } returns emptyList()
            coEvery { completionDao.getCompletions("household-1") } returns emptyList()
            coEvery {
                remoteHouseholdDataSource.upsertMemberSnapshot(any(), any(), any(), any())
            } returns AppResult.Success(Unit)
            coEvery { pendingSyncOperationDao.delete(any()) } just Runs

            val resultDeferred = async { repository.syncPendingOperations() }
            advanceUntilIdle()
            val result = resultDeferred.await()

            assertThat(result).isInstanceOf(AppResult.Success::class.java)
            coVerify {
                remoteHouseholdDataSource.upsertMemberSnapshot(
                    householdId = "household-1",
                    member = match { it.joinedViaInviteId == "invite-1" && it.id == "member-2" },
                    completions = any(),
                    userId = "user-2",
                )
            }
        }

    @Test
    fun `syncPendingOperations resolves householdId for completion delete from payload`() =
        runTest(coroutineRule.dispatcher) {
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

            val resultDeferred = async { repository.syncPendingOperations() }
            advanceUntilIdle()
            val result = resultDeferred.await()

            assertThat(result).isInstanceOf(AppResult.Success::class.java)
            coVerify { remoteHouseholdDataSource.deleteCompletion("household-1", "completion-1") }
        }

    @Test
    fun `syncPendingOperations keeps a pending op and returns Error when the remote delete is rejected`() =
        runTest(coroutineRule.dispatcher) {
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
            coEvery { remoteHouseholdDataSource.deleteCompletion(any(), any()) } returns
                AppResult.Error("Missing or insufficient permissions.")
            coEvery { pendingSyncOperationDao.delete(any()) } just Runs

            val resultDeferred = async { repository.syncPendingOperations() }
            advanceUntilIdle()
            val result = resultDeferred.await()

            assertThat(result).isInstanceOf(AppResult.Error::class.java)
            coVerify(exactly = 0) { pendingSyncOperationDao.delete("op-2") }
            coVerify { syncStateDao.upsert(match { it.pendingOperations == 1 && it.lastErrorMessage != null }) }
        }

    @Test
    fun `syncPendingOperations returns Error and records lastSyncAttemptAt when remote fails`() =
        runTest(coroutineRule.dispatcher) {
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

            val resultDeferred = async { repository.syncPendingOperations() }
            advanceUntilIdle()
            val result = resultDeferred.await()

            assertThat(result).isInstanceOf(AppResult.Error::class.java)
            coVerify { syncStateDao.upsert(match { it.lastErrorMessage == "Missing or insufficient permissions." }) }
        }

    // restoreHouseholdForUser

    @Test
    fun `restoreHouseholdForUser returns Error when fetch fails`() = runTest(coroutineRule.dispatcher) {
        coEvery { remoteHouseholdDataSource.fetchHouseholdSnapshot("user-1") } returns
            AppResult.Error("Network error")

        val result = repository.restoreHouseholdForUser("user-1")

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        assertThat((result as AppResult.Error).message).isEqualTo("Network error")
    }

    @Test
    fun `restoreHouseholdForUser returns Success(false) when no remote snapshot`() = runTest(coroutineRule.dispatcher) {
        coEvery { remoteHouseholdDataSource.fetchHouseholdSnapshot("user-1") } returns AppResult.Success(null)

        val result = repository.restoreHouseholdForUser("user-1")

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat((result as AppResult.Success).value).isFalse()
        coVerify(exactly = 0) { householdDao.upsert(any()) }
    }

    @Test
    fun `restoreHouseholdForUser upserts all snapshot entities and returns Success(true)`() = runTest(
        coroutineRule.dispatcher,
    ) {
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
    fun `restoreHouseholdForUser clears stale participants before re-inserting a completion's`() = runTest(
        coroutineRule.dispatcher,
    ) {
        val snapshot = buildSnapshot()
        coEvery { remoteHouseholdDataSource.fetchHouseholdSnapshot("user-1") } returns AppResult.Success(snapshot)

        repository.restoreHouseholdForUser("user-1")

        coVerifyOrder {
            completionParticipantDao.deleteByCompletionId("completion-1")
            completionParticipantDao.insertAll(match { it.all { p -> p.completionId == "completion-1" } })
        }
    }

    @Test
    fun `restoreHouseholdForUser deduplicates members keeping userId-linked entry`() = runTest(
        coroutineRule.dispatcher,
    ) {
        val members = sampleMembers()
        val duplicateWithoutUserId = members[0].copy(userId = null)
        val snapshot = buildSnapshot(members = listOf(duplicateWithoutUserId, members[0], members[1]))
        coEvery { remoteHouseholdDataSource.fetchHouseholdSnapshot("user-1") } returns AppResult.Success(snapshot)
        coEvery { memberDao.getMembers("household-1") } returns emptyList()

        repository.restoreHouseholdForUser("user-1")

        coVerify(exactly = 1) { memberDao.upsert(match { it.id == "member-1" && it.userId == "user-1" }) }
    }

    @Test
    fun `restoreHouseholdForUser prunes members not in snapshot`() = runTest(coroutineRule.dispatcher) {
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
    fun `restoreHouseholdForUser skips the whole member reconciliation when the household has a pending member op`() =
        runTest(coroutineRule.dispatcher) {
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

            // The pulled snapshot is skipped entirely, not just its prune step - it may predate
            // the not-yet-pushed local change the pending op represents.
            repository.restoreHouseholdForUser("user-1")

            coVerify(exactly = 0) { memberDao.deleteById(any()) }
            coVerify(exactly = 0) { memberDao.upsert(any()) }
        }

    @Test
    fun `restoreHouseholdForUser does not prune a completion with a pending sync operation`() = runTest(
        coroutineRule.dispatcher,
    ) {
        val snapshot = buildSnapshot()
        coEvery { remoteHouseholdDataSource.fetchHouseholdSnapshot("user-1") } returns AppResult.Success(snapshot)
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

        repository.restoreHouseholdForUser("user-1")

        coVerify(exactly = 0) { completionDao.deleteById("unsynced-completion") }
    }

    @Test
    fun `restoreHouseholdForUser does not prune invites when the household has a pending invite op`() =
        runTest(coroutineRule.dispatcher) {
            val snapshot = buildSnapshot()
            coEvery { remoteHouseholdDataSource.fetchHouseholdSnapshot("user-1") } returns AppResult.Success(snapshot)
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

            repository.restoreHouseholdForUser("user-1")

            coVerify(exactly = 0) { inviteDao.deleteById(any()) }
        }

    @Test
    fun `restoreHouseholdForUser deduplicates members preferring placeholder displayName`() = runTest(
        coroutineRule.dispatcher,
    ) {
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
    fun `restoreHouseholdForUser clears local data when current user missing from snapshot members`() = runTest(
        coroutineRule.dispatcher,
    ) {
        val snapshot = buildSnapshot(members = listOf(sampleMembers()[1]))
        coEvery { remoteHouseholdDataSource.fetchHouseholdSnapshot("user-1") } returns AppResult.Success(snapshot)
        coEvery { householdDao.getCurrentHouseholdForUser("user-1") } returns householdEntity

        val result = repository.restoreHouseholdForUser("user-1")

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat((result as AppResult.Success).value).isFalse()
        coVerify { database.clearAll() }
        coVerify(exactly = 0) { householdDao.upsert(any()) }
        coVerify(exactly = 0) { memberDao.upsert(any()) }
    }

    @Test
    fun `restoreHouseholdForUser clears local data when the current user's own row is soft-removed`() = runTest(
        coroutineRule.dispatcher,
    ) {
        val removedSelf = sampleMembers()[0].copy(removedAt = Instant.parse("2026-03-01T10:00:00Z"))
        val snapshot = buildSnapshot(members = listOf(removedSelf, sampleMembers()[1]))
        coEvery { remoteHouseholdDataSource.fetchHouseholdSnapshot("user-1") } returns AppResult.Success(snapshot)
        coEvery { householdDao.getCurrentHouseholdForUser("user-1") } returns householdEntity

        val result = repository.restoreHouseholdForUser("user-1")

        assertThat((result as AppResult.Success).value).isFalse()
        coVerify { database.clearAll() }
        coVerify(exactly = 0) { memberDao.upsert(any()) }
    }

    @Test
    fun `restoreHouseholdForUser does not clear or apply a blank snapshot when no local household existed`() =
        runTest(coroutineRule.dispatcher) {
            // The permission-denied path returns an empty-members snapshot with a blank name /
            // ownerUserId - applySnapshot-ing that would corrupt Room. It must be ignored.
            val snapshot = buildSnapshot(members = emptyList()).copy(
                household = Household(
                    id = "household-1",
                    name = "",
                    ownerUserId = "",
                    inviteCode = "",
                    createdAt = Instant.fromEpochMilliseconds(0),
                ),
            )
            coEvery { remoteHouseholdDataSource.fetchHouseholdSnapshot("user-1") } returns AppResult.Success(snapshot)
            coEvery { householdDao.getCurrentHouseholdForUser("user-1") } returns null

            val result = repository.restoreHouseholdForUser("user-1")

            assertThat((result as AppResult.Success).value).isFalse()
            coVerify(exactly = 0) { database.clearAll() }
            coVerify(exactly = 0) { householdDao.upsert(any()) }
        }

    @Test
    fun `syncPendingOperations calls markInviteConsumed with consumedByMemberId`() = runTest(coroutineRule.dispatcher) {
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

        val resultDeferred = async { repository.syncPendingOperations() }
        advanceUntilIdle()
        val result = resultDeferred.await()

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
