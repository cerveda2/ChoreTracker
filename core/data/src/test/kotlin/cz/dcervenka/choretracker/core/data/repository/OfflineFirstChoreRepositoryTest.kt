package cz.dcervenka.choretracker.core.data.repository

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.SyncRepository
import cz.dcervenka.choretracker.core.database.dao.ChoreDao
import cz.dcervenka.choretracker.core.database.dao.CompletionDao
import cz.dcervenka.choretracker.core.database.dao.MemberDao
import cz.dcervenka.choretracker.core.database.dao.PendingSyncOperationDao
import cz.dcervenka.choretracker.core.database.dao.PersonalChoreDao
import cz.dcervenka.choretracker.core.database.entity.ChoreEntity
import cz.dcervenka.choretracker.core.database.entity.CompletionEntity
import cz.dcervenka.choretracker.core.database.entity.MemberEntity
import cz.dcervenka.choretracker.core.database.entity.PendingSyncOperationEntity
import cz.dcervenka.choretracker.core.database.entity.PersonalChoreEntity
import cz.dcervenka.choretracker.core.model.auth.AppUser
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
import cz.dcervenka.choretracker.core.model.chore.ChoreSchedule
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
import kotlinx.datetime.DayOfWeek
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
    lateinit var personalChoreDao: PersonalChoreDao

    @MockK
    lateinit var syncRepository: SyncRepository

    private lateinit var repository: OfflineFirstChoreRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        coEvery { personalChoreDao.getChore(any(), any()) } returns null
        coEvery { personalChoreDao.getCompletion(any(), any()) } returns null
        coEvery { choreDao.upsert(any()) } just Runs
        coEvery { choreDao.markDeleted(any(), any()) } just Runs
        coEvery { choreDao.updateActive(any(), any()) } just Runs
        coEvery { choreDao.updateName(any(), any()) } just Runs
        coEvery { choreDao.updateSchedule(any(), any(), any(), any()) } just Runs
        coEvery { choreDao.updateCategory(any(), any()) } just Runs
        coEvery { choreDao.updateGroup(any(), any()) } just Runs
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
            personalChoreDao = personalChoreDao,
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
    fun `addChore passes an interval through to the entity`() = runTest {
        val slot = slot<ChoreEntity>()
        coEvery { choreDao.upsert(capture(slot)) } just Runs

        repository.addChore("household-1", "Kitchen", ChoreCategory.COOKING, ChoreSchedule.EveryDays(6))

        assertThat(slot.captured.frequencyDays).isEqualTo(6)
        assertThat(slot.captured.scheduleWeekdays).isEmpty()
        assertThat(slot.captured.scheduleMonthDay).isNull()
    }

    @Test
    fun `addChore stores weekdays with the interval older versions use`() = runTest {
        val slot = slot<ChoreEntity>()
        coEvery { choreDao.upsert(capture(slot)) } just Runs
        val days = setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY)

        repository.addChore("household-1", "Bins", ChoreCategory.OTHER, ChoreSchedule.Weekdays(days))

        assertThat(slot.captured.scheduleWeekdays).isEqualTo(days)
        assertThat(slot.captured.frequencyDays).isEqualTo(4)
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

    // updateChoreSchedule

    @Test
    fun `updateChoreSchedule clears every schedule field and queues an update`() = runTest {
        val slot = slot<PendingSyncOperationEntity>()
        coEvery { pendingSyncOperationDao.upsert(capture(slot)) } just Runs

        repository.updateChoreSchedule("chore-1", null)

        coVerify { choreDao.updateSchedule("chore-1", null, emptySet(), null) }
        assertThat(slot.captured.operationType).isEqualTo("update_schedule")
        assertThat(slot.captured.payload).isEmpty()
    }

    @Test
    fun `updateChoreSchedule stores a month day with the interval older versions use`() = runTest {
        repository.updateChoreSchedule("chore-1", ChoreSchedule.MonthDay(1))

        coVerify { choreDao.updateSchedule("chore-1", 30, emptySet(), 1) }
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

    // group

    @Test
    fun `addChore stores a trimmed group name`() = runTest {
        val slot = slot<ChoreEntity>()
        coEvery { choreDao.upsert(capture(slot)) } just Runs

        repository.addChore("household-1", "Glass", ChoreCategory.OTHER, groupName = "  Trash  ")

        assertThat(slot.captured.groupName).isEqualTo("Trash")
    }

    @Test
    fun `updateChoreGroup trims the name and queues an update`() = runTest {
        val slot = slot<PendingSyncOperationEntity>()
        coEvery { pendingSyncOperationDao.upsert(capture(slot)) } just Runs

        repository.updateChoreGroup("chore-1", "  Trash ")

        coVerify { choreDao.updateGroup("chore-1", "Trash") }
        assertThat(slot.captured.operationType).isEqualTo("update_group")
        assertThat(slot.captured.payload).isEqualTo("Trash")
    }

    @Test
    fun `updateChoreGroup with a blank name takes the chore out of its group`() = runTest {
        repository.updateChoreGroup("chore-1", "   ")

        coVerify { choreDao.updateGroup("chore-1", null) }
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

    // personal chores

    private val personalChore = PersonalChoreEntity(
        id = "personal-1",
        ownerUserId = "user-1",
        name = "Gym",
        isActive = true,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        deletedAt = null,
    )

    @Test
    fun `addPersonalChore stores it for the signed-in user and queues it apart from the household`() = runTest {
        val chore = slot<PersonalChoreEntity>()
        val op = slot<PendingSyncOperationEntity>()
        coEvery { personalChoreDao.upsertChore(capture(chore)) } just Runs
        coEvery { pendingSyncOperationDao.upsert(capture(op)) } just Runs

        val result = repository.addPersonalChore("Gym", ChoreCategory.OTHER, ChoreSchedule.EveryDays(2))

        assertThat((result as AppResult.Success).value).isEqualTo(chore.captured.id)
        assertThat(chore.captured.ownerUserId).isEqualTo("user-1")
        assertThat(chore.captured.frequencyDays).isEqualTo(2)
        assertThat(op.captured.entityType).isEqualTo("personal_chore")
        coVerify(exactly = 0) { choreDao.upsert(any()) }
    }

    @Test
    fun `addPersonalChore is refused in preview mode`() = runTest {
        every { authRepository.authState } returns MutableStateFlow(
            AuthState.Authenticated(AppUser("preview-user", null, "Preview", isPreview = true)),
        )

        val result = repository.addPersonalChore("Gym", ChoreCategory.OTHER, null)

        assertThat((result as AppResult.Error).error).isEqualTo(AppError.PREVIEW_MODE_RESTRICTED)
    }

    @Test
    fun `edits and deletes of a personal chore stay in the personal table`() = runTest {
        val saved = mutableListOf<PersonalChoreEntity>()
        coEvery { personalChoreDao.getChore("personal-1", "user-1") } returns personalChore
        coEvery { personalChoreDao.upsertChore(capture(saved)) } just Runs

        repository.updateChoreName("personal-1", "  Swim ")
        repository.deleteChore("personal-1")

        assertThat(saved[0].name).isEqualTo("Swim")
        assertThat(saved[1].deletedAt).isNotNull()
        assertThat(saved[1].isActive).isFalse()
        coVerify(exactly = 0) { choreDao.updateName(any(), any()) }
        coVerify(exactly = 0) { choreDao.markDeleted(any(), any()) }
        coVerify(exactly = 2) { pendingSyncOperationDao.upsert(match { it.entityType == "personal_chore" }) }
    }
}
