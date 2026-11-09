package cz.dcervenka.choretracker.core.data.repository

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.SyncRepository
import cz.dcervenka.choretracker.core.database.dao.ChoreDao
import cz.dcervenka.choretracker.core.database.dao.CompletionDao
import cz.dcervenka.choretracker.core.database.dao.MemberDao
import cz.dcervenka.choretracker.core.database.dao.PendingSyncOperationDao
import cz.dcervenka.choretracker.core.database.entity.ChoreEntity
import cz.dcervenka.choretracker.core.database.entity.CompletionEntity
import cz.dcervenka.choretracker.core.database.entity.MemberEntity
import cz.dcervenka.choretracker.core.database.entity.PendingSyncOperationEntity
import cz.dcervenka.choretracker.core.model.auth.AppUser
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

class OfflineFirstChoreRepositoryTest {

    @MockK
    lateinit var choreDao: ChoreDao

    @MockK
    lateinit var completionDao: CompletionDao

    @MockK
    lateinit var memberDao: MemberDao

    @MockK
    lateinit var authRepository: AuthRepository

    @MockK
    lateinit var pendingSyncOperationDao: PendingSyncOperationDao

    @MockK
    lateinit var syncRepository: SyncRepository

    private lateinit var repository: OfflineFirstChoreRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        coEvery { choreDao.upsert(any()) } just Runs
        coEvery { choreDao.markDeleted(any(), any()) } just Runs
        coEvery { choreDao.updateActive(any(), any()) } just Runs
        coEvery { choreDao.updateName(any(), any()) } just Runs
        coEvery { choreDao.updateFrequencyDays(any(), any()) } just Runs
        coEvery { choreDao.updateCategory(any(), any()) } just Runs
        coEvery { choreDao.updateRotation(any(), any(), any()) } just Runs
        coEvery { choreDao.getChore(any()) } returns ChoreEntity(
            id = "chore-1",
            householdId = "household-1",
            name = "Kitchen",
            isActive = true,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            deletedAt = null,
        )
        coEvery { memberDao.findByUserId(any(), any()) } returns MemberEntity(
            id = "member-1",
            householdId = "household-1",
            userId = "user-1",
            displayName = "Owner",
            role = "OWNER",
            isCurrentUser = true,
        )
        every { authRepository.authState } returns MutableStateFlow(
            AuthState.Authenticated(AppUser("user-1", "owner@example.com", "Owner")),
        )
        coEvery { pendingSyncOperationDao.upsert(any()) } just Runs
        coEvery { syncRepository.syncPendingOperations() } returns AppResult.Success(Unit)
        repository = OfflineFirstChoreRepository(
            choreDao = choreDao,
            completionDao = completionDao,
            memberDao = memberDao,
            authRepository = authRepository,
            pendingSyncOperationDao = pendingSyncOperationDao,
            syncRepository = syncRepository,
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
    }

    // addChore

    @Test
    fun `addChore upserts entity with correct fields`() = runTest {
        val slot = slot<ChoreEntity>()
        coEvery { choreDao.upsert(capture(slot)) } just Runs

        repository.addChore("household-1", "Kitchen", ChoreCategory.COOKING)

        with(slot.captured) {
            assertThat(householdId).isEqualTo("household-1")
            assertThat(name).isEqualTo("Kitchen")
            assertThat(isActive).isTrue()
            assertThat(deletedAt).isNull()
            assertThat(frequencyDays).isNull()
            assertThat(category).isEqualTo(ChoreCategory.COOKING.name)
        }
    }

    @Test
    fun `addChore passes frequencyDays through to the entity`() = runTest {
        val slot = slot<ChoreEntity>()
        coEvery { choreDao.upsert(capture(slot)) } just Runs

        repository.addChore("household-1", "Kitchen", ChoreCategory.COOKING, frequencyDays = 6)

        assertThat(slot.captured.frequencyDays).isEqualTo(6)
    }

    @Test
    fun `addChore queues upsert pending sync operation`() = runTest {
        val slot = slot<PendingSyncOperationEntity>()
        coEvery { pendingSyncOperationDao.upsert(capture(slot)) } just Runs

        repository.addChore("household-1", "Kitchen", ChoreCategory.COOKING)

        with(slot.captured) {
            assertThat(entityType).isEqualTo("chore")
            assertThat(operationType).isEqualTo("upsert")
            assertThat(payload).isEqualTo("Kitchen")
        }
    }

    @Test
    fun `addChore triggers sync and returns the new chore's id`() = runTest {
        val slot = slot<ChoreEntity>()
        coEvery { choreDao.upsert(capture(slot)) } just Runs

        val result = repository.addChore("household-1", "Kitchen", ChoreCategory.OTHER)

        coVerify(exactly = 1) { syncRepository.syncPendingOperations() }
        assertThat(result).isEqualTo(AppResult.Success(slot.captured.id))
    }

    // observeChores

    @Test
    fun `observeChores filters out soft-deleted chores`() = runTest {
        val active = ChoreEntity(
            id = "chore-1",
            householdId = "household-1",
            name = "Dishes",
            isActive = true,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            deletedAt = null,
        )
        val deleted = ChoreEntity(
            id = "chore-2",
            householdId = "household-1",
            name = "Vacuum",
            isActive = false,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            deletedAt = Instant.parse("2026-02-01T00:00:00Z"),
        )
        every { choreDao.observeChores("household-1") } returns MutableStateFlow(listOf(active, deleted))

        val result = repository.observeChores("household-1").first()

        assertThat(result.map { it.id }).containsExactly("chore-1")
    }

    // deleteChore

    @Test
    fun `deleteChore soft-deletes via markDeleted`() = runTest {
        repository.deleteChore("chore-1")

        coVerify(exactly = 1) { choreDao.markDeleted("chore-1", any()) }
        coVerify(exactly = 0) { choreDao.upsert(any()) }
    }

    @Test
    fun `deleteChore queues delete pending sync operation with choreId as payload`() = runTest {
        val slot = slot<PendingSyncOperationEntity>()
        coEvery { pendingSyncOperationDao.upsert(capture(slot)) } just Runs

        repository.deleteChore("chore-1")

        with(slot.captured) {
            assertThat(entityType).isEqualTo("chore")
            assertThat(entityId).isEqualTo("chore-1")
            assertThat(operationType).isEqualTo("delete")
            assertThat(payload).isEqualTo("chore-1")
        }
    }

    @Test
    fun `deleteChore triggers sync and returns Success`() = runTest {
        val result = repository.deleteChore("chore-1")

        coVerify(exactly = 1) { syncRepository.syncPendingOperations() }
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `member can delete a chore they created before it has completions`() = runTest {
        coEvery { memberDao.findByUserId(any(), any()) } returns MemberEntity(
            id = "member-1", householdId = "household-1", userId = "user-1",
            displayName = "Member", role = "MEMBER", isCurrentUser = true,
        )
        coEvery { choreDao.getChore("chore-1") } returns ChoreEntity(
            id = "chore-1", householdId = "household-1", name = "Kitchen",
            isActive = true, createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            deletedAt = null, createdByUserId = "user-1",
        )
        coEvery { completionDao.getCompletions("household-1") } returns emptyList()

        assertThat(repository.deleteChore("chore-1")).isInstanceOf(AppResult.Success::class.java)
        coVerify { choreDao.markDeleted("chore-1", any()) }
    }

    @Test
    fun `member cannot delete a chore once anyone has completed it`() = runTest {
        coEvery { memberDao.findByUserId(any(), any()) } returns MemberEntity(
            id = "member-1", householdId = "household-1", userId = "user-1",
            displayName = "Member", role = "MEMBER", isCurrentUser = true,
        )
        coEvery { choreDao.getChore("chore-1") } returns ChoreEntity(
            id = "chore-1", householdId = "household-1", name = "Kitchen",
            isActive = true, createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            deletedAt = null, createdByUserId = "user-1",
        )
        coEvery { completionDao.getCompletions("household-1") } returns listOf(
            CompletionEntity(
                id = "completion-1", householdId = "household-1", choreId = "chore-1",
                createdAt = Instant.parse("2026-01-02T00:00:00Z"), createdByUserId = "user-1", note = null,
            ),
        )

        assertThat(repository.deleteChore("chore-1")).isEqualTo(AppResult.Error(AppError.CHORE_DELETE_FORBIDDEN))
        coVerify(exactly = 0) { choreDao.markDeleted(any(), any()) }
    }

    // updateChoreActive

    @Test
    fun `updateChoreActive queues reactivate operation when isActive true`() = runTest {
        val slot = slot<PendingSyncOperationEntity>()
        coEvery { pendingSyncOperationDao.upsert(capture(slot)) } just Runs

        repository.updateChoreActive("chore-1", true)

        assertThat(slot.captured.operationType).isEqualTo("reactivate")
    }

    @Test
    fun `updateChoreActive queues deactivate operation when isActive false`() = runTest {
        val slot = slot<PendingSyncOperationEntity>()
        coEvery { pendingSyncOperationDao.upsert(capture(slot)) } just Runs

        repository.updateChoreActive("chore-1", false)

        assertThat(slot.captured.operationType).isEqualTo("deactivate")
    }

    // updateChoreName

    @Test
    fun `updateChoreName trims whitespace in dao call and pending op payload`() = runTest {
        val choreSlot = slot<String>()
        val opSlot = slot<PendingSyncOperationEntity>()
        coEvery { choreDao.updateName("chore-1", capture(choreSlot)) } just Runs
        coEvery { pendingSyncOperationDao.upsert(capture(opSlot)) } just Runs

        repository.updateChoreName("chore-1", "  Kitchen  ")

        assertThat(choreSlot.captured).isEqualTo("Kitchen")
        assertThat(opSlot.captured.payload).isEqualTo("Kitchen")
        assertThat(opSlot.captured.operationType).isEqualTo("rename")
    }

    // updateChoreFrequencyDays

    @Test
    fun `updateChoreFrequencyDays stores empty string payload for null frequency`() = runTest {
        val slot = slot<PendingSyncOperationEntity>()
        coEvery { pendingSyncOperationDao.upsert(capture(slot)) } just Runs

        repository.updateChoreFrequencyDays("chore-1", null)

        assertThat(slot.captured.operationType).isEqualTo("update_frequency")
        assertThat(slot.captured.payload).isEmpty()
    }

    @Test
    fun `updateChoreFrequencyDays stores frequency as string payload`() = runTest {
        val slot = slot<PendingSyncOperationEntity>()
        coEvery { pendingSyncOperationDao.upsert(capture(slot)) } just Runs

        repository.updateChoreFrequencyDays("chore-1", 7)

        assertThat(slot.captured.payload).isEqualTo("7")
    }

    // updateChoreCategory

    @Test
    fun `updateChoreCategory queues operation with category name as payload`() = runTest {
        val slot = slot<PendingSyncOperationEntity>()
        coEvery { pendingSyncOperationDao.upsert(capture(slot)) } just Runs

        repository.updateChoreCategory("chore-1", ChoreCategory.CLEANING)

        assertThat(slot.captured.operationType).isEqualTo("update_category")
        assertThat(slot.captured.payload).isEqualTo(ChoreCategory.CLEANING.name)
    }

    // rotation

    @Test
    fun `addChore starts counting turns when the chore rotates`() = runTest {
        val slot = slot<ChoreEntity>()
        coEvery { choreDao.upsert(capture(slot)) } just Runs

        repository.addChore("household-1", "Dishes", ChoreCategory.COOKING, rotationMemberIds = listOf("a", "b"))

        assertThat(slot.captured.rotationMemberIds).containsExactly("a", "b").inOrder()
        assertThat(slot.captured.rotationStartedAt).isEqualTo(slot.captured.createdAt)
    }

    @Test
    fun `updateChoreRotation restarts the count and queues the chore for upload`() = runTest {
        val op = slot<PendingSyncOperationEntity>()
        coEvery { pendingSyncOperationDao.upsert(capture(op)) } just Runs

        repository.updateChoreRotation("chore-1", listOf("a", "b"))

        coVerify { choreDao.updateRotation("chore-1", listOf("a", "b"), isNull(inverse = true)) }
        assertThat(op.captured.entityId).isEqualTo("chore-1")
        assertThat(op.captured.operationType).isEqualTo("update_rotation")
    }

    @Test
    fun `clearing the rotation clears its start`() = runTest {
        repository.updateChoreRotation("chore-1", emptyList())

        coVerify { choreDao.updateRotation("chore-1", emptyList(), null) }
    }
}
