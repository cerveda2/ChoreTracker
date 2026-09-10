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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

/**
 * Covers LocalSyncRepository's membership-exit paths (leave household, transfer ownership) - each a
 * single-purpose remote call that never rides the pending-op queue. Split out of
 * LocalSyncRepositoryTest to keep that class under the detekt LargeClass threshold.
 */
class LocalSyncRepositoryMembershipTest {

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

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { authRepository.authState } returns authState
        coEvery { pendingSyncOperationDao.getAll() } returns emptyList()
        every { remoteHouseholdDataSource.observeMembers(any(), any()) } returns MutableStateFlow(emptyList())
        every { remoteHouseholdDataSource.observeCompletions(any()) } returns MutableStateFlow(emptyList())
        every { remoteHouseholdDataSource.observeInvites(any()) } returns MutableStateFlow(emptyList())
        every { remoteHouseholdDataSource.observeChores(any()) } returns MutableStateFlow(emptyList())
        coEvery { householdDao.getCurrentHouseholdForUser(any()) } returns null
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
    fun `leaveHousehold wipes local data after the remote write succeeds`() = runTest(coroutineRule.dispatcher) {
        coEvery { remoteHouseholdDataSource.leaveHousehold("household-1", "user-1") } returns AppResult.Success(Unit)

        val result = repository.leaveHousehold("household-1", "user-1")

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        coVerify { database.clearAll() }
    }

    @Test
    fun `leaveHousehold keeps local data when the remote write fails`() = runTest(coroutineRule.dispatcher) {
        coEvery { remoteHouseholdDataSource.leaveHousehold("household-1", "user-1") } returns AppResult.Error("offline")

        val result = repository.leaveHousehold("household-1", "user-1")

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        coVerify(exactly = 0) { database.clearAll() }
    }

    @Test
    fun `transferOwnership delegates to the remote data source`() = runTest(coroutineRule.dispatcher) {
        coEvery {
            remoteHouseholdDataSource.transferOwnership("household-1", "user-2", "user-2", "user-1")
        } returns AppResult.Success(Unit)

        val result = repository.transferOwnership("household-1", "user-2", "user-2", "user-1")

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        coVerify { remoteHouseholdDataSource.transferOwnership("household-1", "user-2", "user-2", "user-1") }
    }

    @Test
    fun `syncPendingOperations routes a just-transferred owner through owner sync despite a stale local ownerUserId`() =
        runTest(coroutineRule.dispatcher) {
            // households.ownerUserId has no real-time listener, so right after a transfer it can
            // still read the old owner on this device - isOwner must be derived from the caller's
            // own real-time-synced role instead, or their household-level mutations (rename here)
            // get silently routed through performMemberSync and dropped.
            val staleHousehold = HouseholdEntity(
                id = "household-1",
                name = "Home",
                ownerUserId = "someone-else",
                inviteCode = "ABC123",
                createdAt = Instant.parse("2026-01-01T10:00:00Z"),
            )
            val nowOwnerMember = MemberEntity(
                id = "member-1",
                householdId = "household-1",
                userId = "user-1",
                displayName = "Dana",
                role = HouseholdRole.OWNER.name,
                isCurrentUser = true,
            )
            val op = PendingSyncOperationEntity(
                id = "op-1",
                entityType = "member",
                entityId = "household-1",
                operationType = "rename",
                payload = "New Name",
                createdAt = Instant.parse("2026-01-04T10:00:00Z"),
            )
            coEvery { pendingSyncOperationDao.getAll() } returns listOf(op)
            coEvery { householdDao.getHousehold("household-1") } returns staleHousehold
            coEvery { memberDao.findByUserId("household-1", "user-1") } returns nowOwnerMember
            coEvery { memberDao.getMembers("household-1") } returns listOf(nowOwnerMember)
            coEvery { completionParticipantDao.getParticipants("household-1") } returns emptyList()
            coEvery { choreDao.getChores("household-1") } returns emptyList()
            coEvery { completionDao.getCompletions("household-1") } returns emptyList()
            coEvery { inviteDao.getInvites("household-1") } returns emptyList()
            coEvery { remoteHouseholdDataSource.upsertHouseholdSnapshot(any(), any()) } returns AppResult.Success(Unit)
            coEvery { pendingSyncOperationDao.delete(any()) } just Runs
            coEvery { syncStateDao.upsert(any()) } just Runs

            val resultDeferred = async { repository.syncPendingOperations() }
            advanceUntilIdle()
            val result = resultDeferred.await()

            assertThat(result).isInstanceOf(AppResult.Success::class.java)
            coVerify { remoteHouseholdDataSource.upsertHouseholdSnapshot(any(), "user-1") }
            coVerify(exactly = 0) { remoteHouseholdDataSource.upsertMemberSnapshot(any(), any(), any(), any()) }
        }
}
