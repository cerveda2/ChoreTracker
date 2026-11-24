package cz.dcervenka.choretracker.core.data.repository

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.SyncRepository
import cz.dcervenka.choretracker.core.database.dao.ChoreDao
import cz.dcervenka.choretracker.core.database.dao.CompletionDao
import cz.dcervenka.choretracker.core.database.dao.CompletionParticipantDao
import cz.dcervenka.choretracker.core.database.dao.MemberDao
import cz.dcervenka.choretracker.core.database.dao.PendingSyncOperationDao
import cz.dcervenka.choretracker.core.database.dao.PersonalChoreDao
import cz.dcervenka.choretracker.core.database.entity.CompletionEntity
import cz.dcervenka.choretracker.core.database.entity.CompletionParticipantEntity
import cz.dcervenka.choretracker.core.database.entity.MemberEntity
import cz.dcervenka.choretracker.core.database.entity.PendingSyncOperationEntity
import cz.dcervenka.choretracker.core.database.entity.PersonalChoreEntity
import cz.dcervenka.choretracker.core.database.entity.PersonalCompletionEntity
import cz.dcervenka.choretracker.core.model.auth.AppUser
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.model.error.AppError
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
    lateinit var pendingSyncOperationDao: PendingSyncOperationDao

    @MockK
    lateinit var personalChoreDao: PersonalChoreDao

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
        coEvery { personalChoreDao.getChore(any(), any()) } returns null
        coEvery { personalChoreDao.getCompletion(any(), any()) } returns null
        every { authRepository.authState } returns authState
        coEvery { memberDao.findByUserId(any(), any()) } returns MemberEntity(
            id = "member-1",
            householdId = "household-1",
            userId = "user-1",
            displayName = "Dana",
            role = "MEMBER",
            isCurrentUser = true,
        )
        coEvery { completionDao.upsert(any()) } just Runs
        coEvery { participantDao.insertAll(any()) } just Runs
        coEvery { pendingSyncOperationDao.upsert(any()) } just Runs
        coEvery { syncRepository.syncPendingOperations() } returns AppResult.Success(Unit)
        repository = OfflineFirstChoreCompletionRepository(
            completionDao = completionDao,
            participantDao = participantDao,
            choreDao = choreDao,
            memberDao = memberDao,
            pendingSyncOperationDao = pendingSyncOperationDao,
            personalChoreDao = personalChoreDao,
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
    fun `logCompletion rejects a household completion with no participants`() = runTest {
        val result = repository.logCompletion(
            householdId = "household-1",
            choreId = "chore-1",
            participantMemberIds = emptyList(),
            note = null,
            completedAt = null,
        )

        assertThat(result).isEqualTo(AppResult.Error(AppError.PARTICIPANT_REQUIRED))
        coVerify(exactly = 0) { completionDao.upsert(any()) }
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
        coEvery { memberDao.findByUserId("household-1", "user-1") } returns MemberEntity(
            id = "member-1", householdId = "household-1", userId = "user-1",
            displayName = "Dana", role = "OWNER", isCurrentUser = true,
        )
        coEvery { participantDao.deleteByCompletionId(any()) } just Runs

        val result = repository.updateCompletion("completion-1", note = "Edited", participantMemberIds = emptyList())

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        coVerify(exactly = 1) { completionDao.upsert(any()) }
    }

    @Test
    fun `updateCompletion succeeds when the caller is a household admin`() = runTest {
        coEvery { completionDao.getCompletion("completion-1") } returns completion(createdByUserId = "other-user")
        coEvery { memberDao.findByUserId("household-1", "user-1") } returns MemberEntity(
            id = "member-1", householdId = "household-1", userId = "user-1",
            displayName = "Dana", role = "ADMIN", isCurrentUser = true,
        )
        coEvery { participantDao.deleteByCompletionId(any()) } just Runs

        assertThat(repository.updateCompletion("completion-1", "Edited", emptyList()))
            .isInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `updateCompletion fails when the caller is neither the author nor the owner`() = runTest {
        coEvery { completionDao.getCompletion("completion-1") } returns completion(createdByUserId = "other-user")

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

        val result = repository.deleteCompletion("completion-1")

        assertThat(result).isInstanceOf(AppResult.Error::class.java)
        coVerify(exactly = 0) { completionDao.deleteById(any()) }
        coVerify(exactly = 0) { syncRepository.syncPendingOperations() }
    }

    // personal chores

    @Test
    fun `logCompletion on a personal chore stores a personal completion, not a household one`() = runTest {
        val completion = slot<PersonalCompletionEntity>()
        val op = slot<PendingSyncOperationEntity>()
        coEvery { personalChoreDao.getChore("personal-1", "user-1") } returns PersonalChoreEntity(
            id = "personal-1",
            ownerUserId = "user-1",
            name = "Gym",
            isActive = true,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            deletedAt = null,
        )
        coEvery { personalChoreDao.upsertCompletion(capture(completion)) } just Runs
        coEvery { pendingSyncOperationDao.upsert(capture(op)) } just Runs

        val result = repository.logCompletion("household-1", "personal-1", emptyList(), note = " ")

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat(completion.captured.ownerUserId).isEqualTo("user-1")
        assertThat(completion.captured.note).isNull()
        assertThat(op.captured.entityType).isEqualTo("personal_completion")
        coVerify(exactly = 0) { completionDao.upsert(any()) }
        coVerify(exactly = 0) { participantDao.insertAll(any()) }
    }

    @Test
    fun `deleteCompletion of a personal completion queues a personal delete`() = runTest {
        val op = slot<PendingSyncOperationEntity>()
        coEvery { personalChoreDao.getCompletion("personal-completion-1", "user-1") } returns PersonalCompletionEntity(
            id = "personal-completion-1",
            ownerUserId = "user-1",
            choreId = "personal-1",
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            note = null,
        )
        coEvery { personalChoreDao.deleteCompletion(any()) } just Runs
        coEvery { pendingSyncOperationDao.deleteByEntityId(any()) } just Runs
        coEvery { pendingSyncOperationDao.upsert(capture(op)) } just Runs

        repository.deleteCompletion("personal-completion-1")

        coVerify { personalChoreDao.deleteCompletion("personal-completion-1") }
        assertThat(op.captured.entityType).isEqualTo("personal_completion")
        assertThat(op.captured.operationType).isEqualTo("delete")
        coVerify(exactly = 0) { completionDao.deleteById(any()) }
    }

    @Test
    fun `updateCompletion of a personal completion changes its note and queues a personal upsert`() = runTest {
        val completion = slot<PersonalCompletionEntity>()
        val op = slot<PendingSyncOperationEntity>()
        coEvery { completionDao.getCompletion("personal-completion-1") } returns null
        coEvery { personalChoreDao.getCompletion("personal-completion-1", "user-1") } returns PersonalCompletionEntity(
            id = "personal-completion-1",
            ownerUserId = "user-1",
            choreId = "personal-1",
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            note = null,
        )
        coEvery { personalChoreDao.upsertCompletion(capture(completion)) } just Runs
        coEvery { pendingSyncOperationDao.upsert(capture(op)) } just Runs

        val result = repository.updateCompletion("personal-completion-1", "Legs", participantMemberIds = emptyList())

        assertThat(result).isEqualTo(AppResult.Success(Unit))
        assertThat(completion.captured.note).isEqualTo("Legs")
        assertThat(op.captured.entityType).isEqualTo("personal_completion")
        coVerify(exactly = 0) { completionDao.upsert(any()) }
    }
}
