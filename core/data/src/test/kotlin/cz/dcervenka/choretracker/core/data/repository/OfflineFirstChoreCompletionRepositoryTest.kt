package cz.dcervenka.choretracker.core.data.repository

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.SyncRepository
import cz.dcervenka.choretracker.core.database.dao.ChoreDao
import cz.dcervenka.choretracker.core.database.dao.CompletionDao
import cz.dcervenka.choretracker.core.database.dao.CompletionParticipantDao
import cz.dcervenka.choretracker.core.database.dao.HouseholdDao
import cz.dcervenka.choretracker.core.database.dao.MemberDao
import cz.dcervenka.choretracker.core.database.dao.PendingSyncOperationDao
import cz.dcervenka.choretracker.core.database.entity.CompletionEntity
import cz.dcervenka.choretracker.core.database.entity.CompletionParticipantEntity
import cz.dcervenka.choretracker.core.database.entity.HouseholdEntity
import cz.dcervenka.choretracker.core.model.auth.AppUser
import cz.dcervenka.choretracker.core.model.auth.AuthState
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

class OfflineFirstChoreCompletionRepositoryTest {

    @MockK
    lateinit var completionDao: CompletionDao

    @MockK
    lateinit var participantDao: CompletionParticipantDao

    @MockK
    lateinit var choreDao: ChoreDao

    @MockK
    lateinit var memberDao: MemberDao

    @MockK
    lateinit var householdDao: HouseholdDao

    @MockK
    lateinit var pendingSyncOperationDao: PendingSyncOperationDao

    @MockK
    lateinit var authRepository: AuthRepository

    @MockK
    lateinit var syncRepository: SyncRepository

    private val authState = MutableStateFlow<AuthState>(
        AuthState.Authenticated(
            user = AppUser(id = "user-1", email = "dana@example.com", displayName = "Dana"),
        ),
    )

    private lateinit var repository: OfflineFirstChoreCompletionRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { authRepository.authState } returns authState
        coEvery { completionDao.upsert(any()) } just Runs
        coEvery { participantDao.insertAll(any()) } just Runs
        coEvery { pendingSyncOperationDao.upsert(any()) } just Runs
        coEvery { syncRepository.syncPendingOperations() } returns AppResult.Success(Unit)
        repository = OfflineFirstChoreCompletionRepository(
            completionDao = completionDao,
            participantDao = participantDao,
            choreDao = choreDao,
            memberDao = memberDao,
            householdDao = householdDao,
            pendingSyncOperationDao = pendingSyncOperationDao,
            authRepository = authRepository,
            syncRepository = syncRepository,
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
    }

    @Test
    fun `logCompletion returns error when not authenticated`() = runTest {
        authState.value = AuthState.SignedOut

        val result = repository.logCompletion(
            householdId = "household-1",
            choreId = "chore-1",
            participantMemberIds = listOf("member-1"),
            note = null,
            completedAt = null,
        )

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        coVerify(exactly = 0) { completionDao.upsert(any()) }
    }

    @Test
    fun `logCompletion deduplicates participant ids`() = runTest {
        val participantsSlot = slot<List<CompletionParticipantEntity>>()
        coEvery { participantDao.insertAll(capture(participantsSlot)) } just Runs

        repository.logCompletion(
            householdId = "household-1",
            choreId = "chore-1",
            participantMemberIds = listOf("member-1", "member-1", "member-2"),
            note = null,
            completedAt = null,
        )

        assertThat(participantsSlot.captured.map { it.memberId })
            .containsExactly("member-1", "member-2")
    }

    @Test
    fun `logCompletion stores null for blank note`() = runTest {
        val completionSlot = slot<CompletionEntity>()
        coEvery { completionDao.upsert(capture(completionSlot)) } just Runs

        repository.logCompletion(
            householdId = "household-1",
            choreId = "chore-1",
            participantMemberIds = listOf("member-1"),
            note = "   ",
            completedAt = null,
        )

        assertThat(completionSlot.captured.note).isNull()
    }

    @Test
    fun `logCompletion uses provided completedAt timestamp`() = runTest {
        val completionSlot = slot<CompletionEntity>()
        coEvery { completionDao.upsert(capture(completionSlot)) } just Runs
        val backdatedAt = Instant.parse("2026-01-10T08:00:00Z")

        repository.logCompletion(
            householdId = "household-1",
            choreId = "chore-1",
            participantMemberIds = listOf("member-1"),
            note = null,
            completedAt = backdatedAt,
        )

        assertThat(completionSlot.captured.createdAt).isEqualTo(backdatedAt)
    }

    @Test
    fun `logCompletion queues pending sync operation and triggers sync`() = runTest {
        repository.logCompletion(
            householdId = "household-1",
            choreId = "chore-1",
            participantMemberIds = listOf("member-1"),
            note = "Note",
            completedAt = null,
        )

        coVerify(exactly = 1) { pendingSyncOperationDao.upsert(any()) }
        coVerify(exactly = 1) { syncRepository.syncPendingOperations() }
    }

    @Test
    fun `logCompletion returns success for authenticated user`() = runTest {
        val result = repository.logCompletion(
            householdId = "household-1",
            choreId = "chore-1",
            participantMemberIds = listOf("member-1"),
            note = null,
            completedAt = null,
        )

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }

    private fun completion(createdByUserId: String) = CompletionEntity(
        id = "completion-1",
        householdId = "household-1",
        choreId = "chore-1",
        createdAt = Instant.parse("2026-01-10T08:00:00Z"),
        createdByUserId = createdByUserId,
        note = "Original note",
    )

    private fun household(ownerUserId: String) = HouseholdEntity(
        id = "household-1",
        name = "Home",
        ownerUserId = ownerUserId,
        inviteCode = "ABC123",
        createdAt = Instant.parse("2026-01-01T10:00:00Z"),
    )

    @Test
    fun `updateCompletion succeeds when the caller is the original author`() = runTest {
        coEvery { completionDao.getCompletion("completion-1") } returns completion(createdByUserId = "user-1")
        coEvery { participantDao.deleteByCompletionId(any()) } just Runs

        val result = repository.updateCompletion("completion-1", note = "Edited", participantMemberIds = emptyList())

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        coVerify(exactly = 1) { completionDao.upsert(any()) }
    }

    @Test
    fun `updateCompletion succeeds when the caller is the household owner`() = runTest {
        coEvery { completionDao.getCompletion("completion-1") } returns completion(createdByUserId = "other-user")
        coEvery { householdDao.getHousehold("household-1") } returns household(ownerUserId = "user-1")
        coEvery { participantDao.deleteByCompletionId(any()) } just Runs

        val result = repository.updateCompletion("completion-1", note = "Edited", participantMemberIds = emptyList())

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        coVerify(exactly = 1) { completionDao.upsert(any()) }
    }

    @Test
    fun `updateCompletion fails when the caller is neither the author nor the owner`() = runTest {
        coEvery { completionDao.getCompletion("completion-1") } returns completion(createdByUserId = "other-user")
        coEvery { householdDao.getHousehold("household-1") } returns household(ownerUserId = "yet-another-user")

        val result = repository.updateCompletion("completion-1", note = "Edited", participantMemberIds = emptyList())

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        coVerify(exactly = 0) { completionDao.upsert(any()) }
        coVerify(exactly = 0) { syncRepository.syncPendingOperations() }
    }

    @Test
    fun `deleteCompletion succeeds when the caller is the original author`() = runTest {
        coEvery { completionDao.getCompletion("completion-1") } returns completion(createdByUserId = "user-1")
        coEvery { completionDao.deleteById(any()) } just Runs
        coEvery { participantDao.deleteByCompletionId(any()) } just Runs
        coEvery { pendingSyncOperationDao.deleteByEntityId(any()) } just Runs

        val result = repository.deleteCompletion("completion-1")

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        coVerify(exactly = 1) { completionDao.deleteById("completion-1") }
    }

    @Test
    fun `deleteCompletion fails when the caller is neither the author nor the owner`() = runTest {
        coEvery { completionDao.getCompletion("completion-1") } returns completion(createdByUserId = "other-user")
        coEvery { householdDao.getHousehold("household-1") } returns household(ownerUserId = "yet-another-user")

        val result = repository.deleteCompletion("completion-1")

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        coVerify(exactly = 0) { completionDao.deleteById(any()) }
        coVerify(exactly = 0) { syncRepository.syncPendingOperations() }
    }
}
