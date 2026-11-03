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
import cz.dcervenka.choretracker.core.database.entity.HouseholdEntity
import cz.dcervenka.choretracker.core.database.entity.MemberEntity
import cz.dcervenka.choretracker.core.database.entity.PendingSyncOperationEntity
import cz.dcervenka.choretracker.core.model.auth.AppUser
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.model.error.AppError
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import cz.dcervenka.choretracker.core.remote.contract.RemoteHouseholdDataSource
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
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

/**
 * Covers the local member list when a members snapshot reaches the listener while a member op is still
 * pending, and is therefore dropped. memberRows and pendingOps stand in for the Room tables, so assertions
 * are on local state rather than on DAO calls; serverMembers is what Firestore holds.
 */
class LocalSyncRepositoryMemberResyncTest {

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

    private val householdEntity = HouseholdEntity(
        id = "household-1",
        name = "Home",
        ownerUserId = "user-1",
        inviteCode = "ABC123",
        createdAt = Instant.parse("2026-01-01T10:00:00Z"),
    )

    private val owner = MemberEntity(
        id = "member-1",
        householdId = "household-1",
        userId = "user-1",
        displayName = "Dana",
        role = HouseholdRole.OWNER.name,
        isCurrentUser = true,
        email = "dana@example.com",
    )

    private val alex = MemberEntity(
        id = "member-2",
        householdId = "household-1",
        userId = "user-2",
        displayName = "Alex",
        role = HouseholdRole.MEMBER.name,
        isCurrentUser = false,
        email = "alex@example.com",
        joinedViaInviteId = "invite-1",
    )

    private val ownerRenameOp = PendingSyncOperationEntity(
        id = "op-1",
        entityType = "member",
        entityId = "household-1",
        operationType = "rename",
        payload = "Dana",
        createdAt = Instant.parse("2026-01-04T10:00:00Z"),
    )

    private val departedAt = Instant.parse("2026-01-05T10:00:00Z")

    // leaveHousehold and the deleteAccount Cloud Function write the same soft removal: active false,
    // removedAt set, email deleted, name kept so history keeps resolving.
    private val alexDeparted = alex.asRemote().copy(email = null, removedAt = departedAt)

    private val memberRows = linkedMapOf<String, MemberEntity>()
    private val pendingOps = mutableListOf<PendingSyncOperationEntity>()
    private val remoteMembers = MutableSharedFlow<List<HouseholdMember>>()
    private var serverMembers = emptyList<HouseholdMember>()

    private lateinit var repository: LocalSyncRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        listOf(owner, alex).forEach { memberRows[it.id] = it }
        every { authRepository.authState } returns authState
        every { householdDao.observeHouseholdForUser("user-1") } returns MutableStateFlow(householdEntity)
        coEvery { householdDao.getCurrentHouseholdForUser(any()) } returns null
        coEvery { householdDao.getHousehold("household-1") } returns householdEntity
        coEvery { memberDao.getMembers("household-1") } answers { memberRows.values.toList() }
        coEvery { memberDao.findByUserId("household-1", any()) } answers {
            memberRows.values.find { it.userId == secondArg<String>() }
        }
        coEvery { memberDao.upsert(any()) } answers { memberRows[firstArg<MemberEntity>().id] = firstArg() }
        coEvery { memberDao.deleteById(any()) } answers { memberRows.remove(firstArg<String>()) }
        coEvery { pendingSyncOperationDao.getAll() } answers { pendingOps.toList() }
        coEvery { pendingSyncOperationDao.delete(any()) } answers { pendingOps.removeAll { it.id == firstArg<String>() } }
        coEvery { completionParticipantDao.getParticipants("household-1") } returns emptyList()
        coEvery { completionDao.getCompletions("household-1") } returns emptyList()
        coEvery { choreDao.getChores("household-1") } returns emptyList()
        coEvery { inviteDao.getInvites("household-1") } returns emptyList()
        coEvery { syncStateDao.upsert(any()) } just Runs
        coEvery { database.clearAll() } just Runs
        every { remoteHouseholdDataSource.observeMembers("household-1", "user-1") } returns remoteMembers
        every { remoteHouseholdDataSource.observeCompletions(any()) } returns emptyFlow()
        every { remoteHouseholdDataSource.observeInvites(any()) } returns emptyFlow()
        every { remoteHouseholdDataSource.observeChores(any()) } returns emptyFlow()
        coEvery { remoteHouseholdDataSource.upsertHouseholdSnapshot(any(), "user-1") } returns AppResult.Success(Unit)
        coEvery { remoteHouseholdDataSource.fetchMembers("household-1", "user-1") } answers {
            AppResult.Success(serverMembers)
        }
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
    fun `owner's local list shows a member who left while a member op was pending`() =
        runTest(coroutineRule.dispatcher) {
            deliverWhileMemberOpPending(listOf(owner.asRemote(), alexDeparted))
            syncPendingOperations()

            assertThat(pendingOps).isEmpty()
            assertThat(memberRows.getValue("member-2").removedAt).isEqualTo(departedAt)
            assertThat(memberRows.getValue("member-2").email).isNull()
        }

    @Test
    fun `owner's local list shows a member whose account was deleted while a member op was pending`() =
        runTest(coroutineRule.dispatcher) {
            deliverWhileMemberOpPending(listOf(owner.asRemote(), alexDeparted))
            syncPendingOperations()

            assertThat(pendingOps).isEmpty()
            assertThat(memberRows.getValue("member-2").removedAt).isEqualTo(departedAt)
            assertThat(memberRows.getValue("member-2").displayName).isEqualTo("Alex")
        }

    @Test
    fun `owner's local list shows a member who rejoined while a member op was pending`() =
        runTest(coroutineRule.dispatcher) {
            memberRows["member-2"] = alex.copy(email = null, removedAt = departedAt)

            deliverWhileMemberOpPending(listOf(owner.asRemote(), alex.asRemote().copy(joinedViaInviteId = "invite-2")))
            syncPendingOperations()

            assertThat(pendingOps).isEmpty()
            assertThat(memberRows.getValue("member-2").removedAt).isNull()
            assertThat(memberRows.getValue("member-2").joinedViaInviteId).isEqualTo("invite-2")
        }

    @Test
    fun `a member change queued during the push is not overwritten until it is pushed too`() =
        runTest(coroutineRule.dispatcher) {
            val sam = MemberEntity(
                id = "member-3",
                householdId = "household-1",
                userId = null,
                displayName = "Sam",
                role = HouseholdRole.MEMBER.name,
                isCurrentUser = false,
            )
            val addSamOp = ownerRenameOp.copy(id = "op-2", operationType = "upsert", payload = "Sam")
            coEvery { remoteHouseholdDataSource.upsertHouseholdSnapshot(any(), "user-1") } answers {
                memberRows[sam.id] = sam
                pendingOps += addSamOp
                AppResult.Success(Unit)
            }

            deliverWhileMemberOpPending(listOf(owner.asRemote(), alexDeparted))
            syncPendingOperations()

            assertThat(pendingOps).containsExactly(addSamOp)
            assertThat(memberRows).containsKey("member-3")
            assertThat(memberRows.getValue("member-2").removedAt).isNull()

            coEvery { remoteHouseholdDataSource.upsertHouseholdSnapshot(any(), "user-1") } answers {
                serverMembers = serverMembers + sam.asRemote()
                AppResult.Success(Unit)
            }
            syncPendingOperations()

            assertThat(pendingOps).isEmpty()
            assertThat(memberRows).containsKey("member-3")
            assertThat(memberRows.getValue("member-2").removedAt).isEqualTo(departedAt)
        }

    @Test
    fun `a failed member re-read keeps the sync successful and local rows as they were`() =
        runTest(coroutineRule.dispatcher) {
            coEvery { remoteHouseholdDataSource.fetchMembers("household-1", "user-1") } returns
                AppResult.Error(AppError.NETWORK)

            deliverWhileMemberOpPending(listOf(owner.asRemote(), alexDeparted))
            syncPendingOperations()

            assertThat(pendingOps).isEmpty()
            assertThat(memberRows.getValue("member-2").removedAt).isNull()
        }

    @Test
    fun `a sync without member ops does not re-read members`() = runTest(coroutineRule.dispatcher) {
        pendingOps += ownerRenameOp.copy(entityType = "household", operationType = "upsert", payload = "household-1")

        syncPendingOperations()

        assertThat(pendingOps).isEmpty()
        coVerify(exactly = 0) { remoteHouseholdDataSource.fetchMembers(any(), any()) }
    }

    // Firestore delivers a change to the listener once; a later server read returns the same state.
    private suspend fun TestScope.deliverWhileMemberOpPending(remote: List<HouseholdMember>) {
        pendingOps += ownerRenameOp
        advanceUntilIdle()
        val rowsBefore = memberRows.toMap()
        serverMembers = remote
        remoteMembers.emit(remote)
        advanceUntilIdle()
        assertThat(memberRows).containsExactlyEntriesIn(rowsBefore)
    }

    private suspend fun TestScope.syncPendingOperations() {
        val resultDeferred = async { repository.syncPendingOperations() }
        advanceUntilIdle()
        assertThat(resultDeferred.await()).isInstanceOf(AppResult.Success::class.java)
    }

    private fun MemberEntity.asRemote() = HouseholdMember(
        id = id,
        householdId = householdId,
        userId = userId,
        displayName = displayName,
        role = HouseholdRole.valueOf(role),
        isCurrentUser = isCurrentUser,
        email = email,
        joinedViaInviteId = joinedViaInviteId,
        removedAt = removedAt,
    )
}
