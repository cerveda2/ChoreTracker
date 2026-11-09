package cz.dcervenka.choretracker.core.data.repository

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.SyncRepository
import cz.dcervenka.choretracker.core.database.dao.ChoreRequestDao
import cz.dcervenka.choretracker.core.database.dao.MemberDao
import cz.dcervenka.choretracker.core.database.dao.PendingSyncOperationDao
import cz.dcervenka.choretracker.core.database.entity.ChoreRequestEntity
import cz.dcervenka.choretracker.core.database.entity.MemberEntity
import cz.dcervenka.choretracker.core.database.entity.PendingSyncOperationEntity
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

class OfflineFirstChoreRequestRepositoryTest {

    @MockK
    lateinit var choreRequestDao: ChoreRequestDao

    @MockK
    lateinit var memberDao: MemberDao

    @MockK
    lateinit var pendingSyncOperationDao: PendingSyncOperationDao

    @MockK
    lateinit var authRepository: AuthRepository

    @MockK
    lateinit var syncRepository: SyncRepository

    private val authState = MutableStateFlow<AuthState>(AuthState.Authenticated(AppUser("user-me", null, "Me")))

    private lateinit var repository: OfflineFirstChoreRequestRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { authRepository.authState } returns authState
        coEvery { memberDao.findByUserId("household-1", "user-me") } returns member("member-me", "user-me")
        coEvery { memberDao.findById("household-1", "member-sam") } returns member("member-sam", "user-sam")
        coEvery { memberDao.findById("household-1", "member-placeholder") } returns member("member-placeholder", null)
        coEvery { choreRequestDao.insertBatch(any(), any()) } just Runs
        coEvery { choreRequestDao.updateState(any(), any(), any()) } just Runs
        coEvery { pendingSyncOperationDao.upsert(any()) } just Runs
        coEvery { syncRepository.syncPendingOperations() } returns AppResult.Success(Unit)
        repository = OfflineFirstChoreRequestRepository(
            choreRequestDao = choreRequestDao,
            memberDao = memberDao,
            pendingSyncOperationDao = pendingSyncOperationDao,
            authRepository = authRepository,
            syncRepository = syncRepository,
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
    }

    @Test
    fun `sending writes one request per chore under one batch and queues the batch`() = runTest {
        val requests = slot<List<ChoreRequestEntity>>()
        val operation = slot<PendingSyncOperationEntity>()
        coEvery { choreRequestDao.insertBatch(capture(requests), capture(operation)) } just Runs

        val result = repository.sendRequests("household-1", "member-sam", listOf("dishes", "bins", "dishes"), " Today ")

        assertThat(result).isEqualTo(AppResult.Success(Unit))
        assertThat(requests.captured.map { it.choreId }).containsExactly("dishes", "bins")
        assertThat(requests.captured.map { it.batchId }.distinct()).containsExactly(operation.captured.entityId)
        with(requests.captured.first()) {
            assertThat(fromMemberId).isEqualTo("member-me")
            assertThat(toUserId).isEqualTo("user-sam")
            assertThat(createdByUserId).isEqualTo("user-me")
            assertThat(note).isEqualTo("Today")
            assertThat(state).isEqualTo("OPEN")
        }
        assertThat(operation.captured.entityType).isEqualTo("request")
        assertThat(operation.captured.operationType).isEqualTo("send")
        coVerify { syncRepository.syncPendingOperations() }
    }

    @Test
    fun `a placeholder or yourself can't be asked`() = runTest {
        assertThat(repository.sendRequests("household-1", "member-placeholder", listOf("dishes"), null))
            .isEqualTo(AppResult.Error(AppError.MEMBER_NOT_JOINED))
        coEvery { memberDao.findById("household-1", "member-me") } returns member("member-me", "user-me")
        assertThat(repository.sendRequests("household-1", "member-me", listOf("dishes"), null))
            .isEqualTo(AppResult.Error(AppError.MEMBER_NOT_FOUND))
        coVerify(exactly = 0) { choreRequestDao.insertBatch(any(), any()) }
    }

    @Test
    fun `only the recipient declines and only the sender cancels`() = runTest {
        coEvery { choreRequestDao.getRequest("asked-of-me") } returns request(toUserId = "user-me", sentBy = "user-sam")
        coEvery { choreRequestDao.getRequest("sent-by-me") } returns request(toUserId = "user-sam", sentBy = "user-me")

        assertThat(repository.cancelRequest("asked-of-me")).isEqualTo(AppResult.Error(AppError.PERMISSION_DENIED))
        assertThat(repository.declineRequest("sent-by-me")).isEqualTo(AppResult.Error(AppError.PERMISSION_DENIED))
        assertThat(repository.declineRequest("asked-of-me")).isEqualTo(AppResult.Success(Unit))
        assertThat(repository.cancelRequest("sent-by-me")).isEqualTo(AppResult.Success(Unit))

        coVerify { choreRequestDao.updateState("asked-of-me", "DECLINED", any()) }
        coVerify { choreRequestDao.updateState("sent-by-me", "CANCELLED", any()) }
        coVerify(exactly = 2) { pendingSyncOperationDao.upsert(match { it.operationType == "resolve" }) }
    }

    @Test
    fun `resolving an already resolved request changes nothing`() = runTest {
        coEvery { choreRequestDao.getRequest("done") } returns request(toUserId = "user-me", sentBy = "user-sam")
            .copy(state = "CANCELLED")

        assertThat(repository.declineRequest("done")).isEqualTo(AppResult.Success(Unit))

        coVerify(exactly = 0) { choreRequestDao.updateState(any(), any(), any()) }
        coVerify(exactly = 0) { pendingSyncOperationDao.upsert(any()) }
    }

    private fun member(id: String, userId: String?) = MemberEntity(
        id = id,
        householdId = "household-1",
        userId = userId,
        displayName = id,
        role = "MEMBER",
        isCurrentUser = userId == "user-me",
    )

    private fun request(toUserId: String, sentBy: String) = ChoreRequestEntity(
        id = "request-1",
        householdId = "household-1",
        batchId = "batch-1",
        choreId = "dishes",
        fromMemberId = "member-from",
        toMemberId = "member-to",
        toUserId = toUserId,
        createdByUserId = sentBy,
        note = null,
        createdAt = Instant.parse("2026-03-01T00:00:00Z"),
        state = "OPEN",
        resolvedAt = null,
    )
}
