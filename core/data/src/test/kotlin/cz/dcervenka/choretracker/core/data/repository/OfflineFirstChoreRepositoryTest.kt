package cz.dcervenka.choretracker.core.data.repository

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.data.contract.SyncRepository
import cz.dcervenka.choretracker.core.database.dao.ChoreDao
import cz.dcervenka.choretracker.core.database.dao.PendingSyncOperationDao
import cz.dcervenka.choretracker.core.database.entity.ChoreEntity
import cz.dcervenka.choretracker.core.database.entity.PendingSyncOperationEntity
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
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
        coEvery { pendingSyncOperationDao.upsert(any()) } just Runs
        coEvery { syncRepository.syncPendingOperations() } returns AppResult.Success(Unit)
        repository = OfflineFirstChoreRepository(
            choreDao = choreDao,
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
    fun `addChore triggers sync and returns Success`() = runTest {
        val result = repository.addChore("household-1", "Kitchen", ChoreCategory.OTHER)

        coVerify(exactly = 1) { syncRepository.syncPendingOperations() }
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
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
}
