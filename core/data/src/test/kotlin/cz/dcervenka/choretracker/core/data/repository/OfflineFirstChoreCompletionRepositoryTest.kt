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
import cz.dcervenka.choretracker.core.database.entity.CompletionEntity
import cz.dcervenka.choretracker.core.database.entity.CompletionParticipantEntity
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
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
            pendingSyncOperationDao = pendingSyncOperationDao,
            authRepository = authRepository,
            syncRepository = syncRepository,
        )
    }

    @Test
    fun `logCompletion returns error when not authenticated`() = runBlocking {
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
    fun `logCompletion deduplicates participant ids`() = runBlocking<Unit> {
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
    fun `logCompletion stores null for blank note`() = runBlocking {
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
    fun `logCompletion uses provided completedAt timestamp`() = runBlocking {
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
    fun `logCompletion queues pending sync operation and triggers sync`() = runBlocking {
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
    fun `logCompletion returns success for authenticated user`() = runBlocking {
        val result = repository.logCompletion(
            householdId = "household-1",
            choreId = "chore-1",
            participantMemberIds = listOf("member-1"),
            note = null,
            completedAt = null,
        )

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }
}
