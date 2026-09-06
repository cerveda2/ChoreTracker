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
import cz.dcervenka.choretracker.core.database.entity.HouseholdEntity
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

/**
 * Covers only LocalSyncRepository's own decision of whether to subscribe to the real-time
 * listeners at all (its init-block authState/household gating). What happens once subscribed -
 * applying a member/completion/invite/chore snapshot to Room - is RealtimeSyncApplier's
 * responsibility; see RealtimeSyncApplierTest.
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

    private val householdEntity = HouseholdEntity(
        id = "household-1",
        name = "Home",
        ownerUserId = "user-1",
        inviteCode = "ABC123",
        createdAt = Instant.parse("2026-01-01T10:00:00Z"),
    )

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
        LocalSyncRepository(
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
}
