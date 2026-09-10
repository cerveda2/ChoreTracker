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
import cz.dcervenka.choretracker.core.model.auth.AppUser
import cz.dcervenka.choretracker.core.model.auth.AuthState
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

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
}
