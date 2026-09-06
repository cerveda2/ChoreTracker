package cz.dcervenka.choretracker.core.sync.repository

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
import cz.dcervenka.choretracker.core.database.entity.CompletionEntity
import cz.dcervenka.choretracker.core.database.entity.HouseholdEntity
import cz.dcervenka.choretracker.core.database.entity.InviteEntity
import cz.dcervenka.choretracker.core.database.entity.MemberEntity
import cz.dcervenka.choretracker.core.model.auth.AppUser
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.model.chore.ChoreCompletion
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import cz.dcervenka.choretracker.core.remote.contract.RemoteHouseholdDataSource
import cz.dcervenka.choretracker.core.test.mock.sampleChore
import cz.dcervenka.choretracker.core.test.mock.sampleInvite
import cz.dcervenka.choretracker.core.test.mock.sampleMembers
import cz.dcervenka.choretracker.core.test.rule.TestCoroutineRule
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

/**
 * Split out from LocalSyncRepositoryTest (detekt's LargeClass threshold) - covers only the
 * real-time listener subscription started from LocalSyncRepository's init block
 * (see LocalSyncRepository.observeRealtimeUpdates). Push/pull sync coverage
 * (syncPendingOperations, restoreHouseholdForUser) stays in LocalSyncRepositoryTest.
 */
class LocalSyncRepositoryRealtimeTest {

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
        coEvery { householdDao.getCurrentHouseholdForUser(any()) } returns null
        // Paused dispatcher means the init-block subscription never runs unless a test
        // explicitly advances it; default to a no-op household so it stays inert until then.
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

    @Test
    fun `real-time sync does not subscribe when no local household exists`() = runTest(coroutineRule.dispatcher) {
        every { householdDao.observeHouseholdForUser("user-1") } returns MutableStateFlow(null)

        advanceUntilIdle()

        coVerify(exactly = 0) { remoteHouseholdDataSource.observeMembers(any(), any()) }
        coVerify(exactly = 0) { remoteHouseholdDataSource.observeCompletions(any()) }
    }

    @Test
    fun `real-time sync does not subscribe when signed out`() = runTest(coroutineRule.dispatcher) {
        every { householdDao.observeHouseholdForUser("user-1") } returns MutableStateFlow(householdEntity)
        authState.value = AuthState.SignedOut

        advanceUntilIdle()

        coVerify(exactly = 0) { remoteHouseholdDataSource.observeMembers(any(), any()) }
        coVerify(exactly = 0) { remoteHouseholdDataSource.observeCompletions(any()) }
    }

    @Test
    fun `real-time sync upserts members received from the remote listener`() = runTest(coroutineRule.dispatcher) {
        every { householdDao.observeHouseholdForUser("user-1") } returns MutableStateFlow(householdEntity)
        every { remoteHouseholdDataSource.observeMembers("household-1", "user-1") } returns
            MutableStateFlow(sampleMembers())

        advanceUntilIdle()

        coVerify { memberDao.upsert(match { it.id == "member-1" }) }
        coVerify { memberDao.upsert(match { it.id == "member-2" }) }
    }

    @Test
    fun `real-time sync prunes members no longer present in the remote listener`() =
        runTest(coroutineRule.dispatcher) {
            every { householdDao.observeHouseholdForUser("user-1") } returns MutableStateFlow(householdEntity)
            every { remoteHouseholdDataSource.observeMembers("household-1", "user-1") } returns
                MutableStateFlow(listOf(sampleMembers()[0]))
            val staleMember = MemberEntity(
                id = "stale-member",
                householdId = "household-1",
                userId = null,
                displayName = "Gone",
                role = HouseholdRole.MEMBER.name,
                isCurrentUser = false,
            )
            coEvery { memberDao.getMembers("household-1") } returns listOf(memberEntity, staleMember)

            advanceUntilIdle()

            coVerify { memberDao.deleteById("stale-member") }
            coVerify(exactly = 0) { memberDao.deleteById("member-1") }
        }

    @Test
    fun `real-time sync clears local data when current user missing from remote members`() =
        runTest(coroutineRule.dispatcher) {
            every { householdDao.observeHouseholdForUser("user-1") } returns MutableStateFlow(householdEntity)
            every { remoteHouseholdDataSource.observeMembers("household-1", "user-1") } returns
                MutableStateFlow(listOf(sampleMembers()[1]))

            advanceUntilIdle()

            coVerify { database.clearAll() }
            coVerify(exactly = 0) { memberDao.upsert(any()) }
        }

    @Test
    fun `real-time sync upserts completions and replaces their participants`() = runTest(coroutineRule.dispatcher) {
        val completion = ChoreCompletion(
            id = "completion-1",
            householdId = "household-1",
            choreId = "chore-1",
            createdAt = Instant.parse("2026-03-30T10:00:00Z"),
            createdByUserId = "user-1",
            note = null,
            participantMemberIds = listOf("member-1", "member-2"),
        )
        every { householdDao.observeHouseholdForUser("user-1") } returns MutableStateFlow(householdEntity)
        every { remoteHouseholdDataSource.observeCompletions("household-1") } returns MutableStateFlow(listOf(completion))

        advanceUntilIdle()

        coVerify { completionDao.upsert(match { it.id == "completion-1" }) }
        coVerify { completionParticipantDao.deleteByCompletionId("completion-1") }
        coVerify {
            completionParticipantDao.insertAll(
                match { participants -> participants.map { it.memberId }.toSet() == setOf("member-1", "member-2") },
            )
        }
    }

    @Test
    fun `real-time sync prunes completions no longer present in the remote listener`() =
        runTest(coroutineRule.dispatcher) {
            every { householdDao.observeHouseholdForUser("user-1") } returns MutableStateFlow(householdEntity)
            every { remoteHouseholdDataSource.observeCompletions("household-1") } returns MutableStateFlow(emptyList())
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

            advanceUntilIdle()

            coVerify { completionDao.deleteById("stale-completion") }
        }

    @Test
    fun `real-time sync upserts invites received from the remote listener`() = runTest(coroutineRule.dispatcher) {
        val invite = sampleInvite()
        every { householdDao.observeHouseholdForUser("user-1") } returns MutableStateFlow(householdEntity)
        every { remoteHouseholdDataSource.observeInvites("household-1") } returns MutableStateFlow(listOf(invite))

        advanceUntilIdle()

        coVerify { inviteDao.upsert(match { it.id == invite.id && it.consumedAt == invite.consumedAt }) }
    }

    @Test
    fun `real-time sync prunes invites no longer present in the remote listener`() =
        runTest(coroutineRule.dispatcher) {
            every { householdDao.observeHouseholdForUser("user-1") } returns MutableStateFlow(householdEntity)
            every { remoteHouseholdDataSource.observeInvites("household-1") } returns MutableStateFlow(emptyList())
            coEvery { inviteDao.getInvites("household-1") } returns listOf(
                InviteEntity(
                    id = "stale-invite",
                    householdId = "household-1",
                    code = "STALE123",
                    createdAt = Instant.parse("2026-03-30T10:00:00Z"),
                    consumedAt = null,
                ),
            )

            advanceUntilIdle()

            coVerify { inviteDao.deleteById("stale-invite") }
        }

    @Test
    fun `real-time sync upserts chores received from the remote listener`() = runTest(coroutineRule.dispatcher) {
        val chore = sampleChore()
        every { householdDao.observeHouseholdForUser("user-1") } returns MutableStateFlow(householdEntity)
        every { remoteHouseholdDataSource.observeChores("household-1") } returns MutableStateFlow(listOf(chore))

        advanceUntilIdle()

        coVerify { choreDao.upsert(match { it.id == chore.id && it.name == chore.name }) }
    }
}
