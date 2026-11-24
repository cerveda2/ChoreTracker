package cz.dcervenka.choretracker.core.data.repository

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.ChoreRepository
import cz.dcervenka.choretracker.core.data.contract.SyncRepository
import cz.dcervenka.choretracker.core.data.di.ApplicationScope
import cz.dcervenka.choretracker.core.data.mapper.asModel
import cz.dcervenka.choretracker.core.database.dao.ChoreDao
import cz.dcervenka.choretracker.core.database.dao.CompletionDao
import cz.dcervenka.choretracker.core.database.dao.MemberDao
import cz.dcervenka.choretracker.core.database.dao.PendingSyncOperationDao
import cz.dcervenka.choretracker.core.database.dao.PersonalChoreDao
import cz.dcervenka.choretracker.core.database.entity.ChoreEntity
import cz.dcervenka.choretracker.core.database.entity.PendingSyncOperationEntity
import cz.dcervenka.choretracker.core.database.entity.PersonalChoreEntity
import cz.dcervenka.choretracker.core.database.mapper.asModel
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
import cz.dcervenka.choretracker.core.model.chore.ChoreSchedule
import cz.dcervenka.choretracker.core.model.chore.frequencyDays
import cz.dcervenka.choretracker.core.model.error.AppError
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

@Singleton
@Suppress("ReturnCount", "TooManyFunctions")
class OfflineFirstChoreRepository @Inject constructor(
    private val choreDao: ChoreDao,
    private val completionDao: CompletionDao,
    private val memberDao: MemberDao,
    private val authRepository: AuthRepository,
    private val pendingSyncOperationDao: PendingSyncOperationDao,
    private val personalChoreDao: PersonalChoreDao,
    private val syncRepository: SyncRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : ChoreRepository {

    override fun observeChores(householdId: String): Flow<List<Chore>> =
        choreDao.observeChores(householdId).map { chores ->
            chores.filter { it.deletedAt == null }.map(ChoreEntity::asModel)
        }

    override fun observeCompletedChoreIds(householdId: String): Flow<Set<String>> =
        completionDao.observeCompletions(householdId).map { completions ->
            completions.map { it.choreId }.toSet()
        }

    override fun observePersonalChores(): Flow<List<Chore>> = authRepository.authState
        .map { (it as? AuthState.Authenticated)?.user?.id }
        .distinctUntilChanged()
        .flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                personalChoreDao.observeChores(userId).map { chores -> chores.map(PersonalChoreEntity::asModel) }
            }
        }

    override suspend fun addPersonalChore(name: String, category: ChoreCategory, schedule: ChoreSchedule?): AppResult<String> {
        Timber.d("addPersonalChore: name=$name category=$category schedule=$schedule")
        val user = (authRepository.authState.first() as? AuthState.Authenticated)?.user
            ?: return AppResult.Error(AppError.NOT_SIGNED_IN)
        if (user.isPreview) return AppResult.Error(AppError.PREVIEW_MODE_RESTRICTED)
        val choreId = UUID.randomUUID().toString()
        personalChoreDao.upsertChore(
            PersonalChoreEntity(
                id = choreId,
                ownerUserId = user.id,
                name = name,
                isActive = true,
                createdAt = Clock.System.now(),
                deletedAt = null,
                frequencyDays = schedule?.frequencyDays,
                category = category.name,
                scheduleWeekdays = (schedule as? ChoreSchedule.Weekdays)?.days.orEmpty(),
                scheduleMonthDay = (schedule as? ChoreSchedule.MonthDay)?.day,
            ),
        )
        queuePersonalChore(choreId)
        return AppResult.Success(choreId)
    }

    override suspend fun addChore(
        householdId: String,
        name: String,
        category: ChoreCategory,
        schedule: ChoreSchedule?,
        rotationMemberIds: List<String>,
        groupName: String?,
    ): AppResult<String> {
        Timber.d("addChore: householdId=$householdId name=$name category=$category schedule=$schedule")
        val userId = currentUserId() ?: return AppResult.Error(AppError.NOT_SIGNED_IN)
        val membership = memberDao.findByUserId(householdId, userId)
            ?: return AppResult.Error(AppError.OWN_MEMBERSHIP_NOT_FOUND)
        if (membership.removedAt != null) return AppResult.Error(AppError.OWN_MEMBERSHIP_NOT_FOUND)
        val choreId = UUID.randomUUID().toString()
        val now = Clock.System.now()
        choreDao.upsert(
            ChoreEntity(
                id = choreId,
                householdId = householdId,
                name = name,
                isActive = true,
                createdAt = now,
                deletedAt = null,
                frequencyDays = schedule?.frequencyDays,
                category = category.name,
                createdByUserId = userId,
                rotationMemberIds = rotationMemberIds,
                rotationStartedAt = now.takeIf { rotationMemberIds.isNotEmpty() },
                scheduleWeekdays = (schedule as? ChoreSchedule.Weekdays)?.days.orEmpty(),
                scheduleMonthDay = (schedule as? ChoreSchedule.MonthDay)?.day,
                groupName = groupName.normalizedGroupName(),
            ),
        )
        pendingSyncOperationDao.upsert(
            PendingSyncOperationEntity(
                id = UUID.randomUUID().toString(),
                entityType = "chore",
                entityId = choreId,
                operationType = "upsert",
                payload = name,
                createdAt = Clock.System.now(),
            ),
        )
        scope.launch { syncRepository.syncPendingOperations() }
        return AppResult.Success(choreId)
    }

    override suspend fun deleteChore(choreId: String): EmptyResult {
        Timber.d("deleteChore: choreId=$choreId")
        updatePersonalChore(choreId) { it.copy(isActive = false, deletedAt = Clock.System.now()) }?.let { return it }
        val chore = choreDao.getChore(choreId) ?: return AppResult.Error(AppError.CHORE_DELETE_FORBIDDEN)
        permissionError(chore, deleting = true)?.let { return AppResult.Error(it) }
        choreDao.markDeleted(choreId, Clock.System.now())
        pendingSyncOperationDao.upsert(
            PendingSyncOperationEntity(
                id = UUID.randomUUID().toString(),
                entityType = "chore",
                entityId = choreId,
                operationType = "delete",
                payload = choreId,
                createdAt = Clock.System.now(),
            ),
        )
        scope.launch { syncRepository.syncPendingOperations() }
        return AppResult.Success(Unit)
    }

    override suspend fun updateChoreActive(choreId: String, isActive: Boolean): EmptyResult {
        Timber.d("updateChoreActive: choreId=$choreId isActive=$isActive")
        updatePersonalChore(choreId) { it.copy(isActive = isActive) }?.let { return it }
        editError(choreId)?.let { return AppResult.Error(it) }
        choreDao.updateActive(choreId, isActive)
        pendingSyncOperationDao.upsert(
            PendingSyncOperationEntity(
                id = UUID.randomUUID().toString(),
                entityType = "chore",
                entityId = choreId,
                operationType = if (isActive) "reactivate" else "deactivate",
                payload = isActive.toString(),
                createdAt = Clock.System.now(),
            ),
        )
        scope.launch { syncRepository.syncPendingOperations() }
        return AppResult.Success(Unit)
    }

    override suspend fun updateChoreName(choreId: String, name: String): EmptyResult {
        Timber.d("updateChoreName: choreId=$choreId name=$name")
        updatePersonalChore(choreId) { it.copy(name = name.trim()) }?.let { return it }
        editError(choreId)?.let { return AppResult.Error(it) }
        choreDao.updateName(choreId, name.trim())
        pendingSyncOperationDao.upsert(
            PendingSyncOperationEntity(
                id = UUID.randomUUID().toString(),
                entityType = "chore",
                entityId = choreId,
                operationType = "rename",
                payload = name.trim(),
                createdAt = Clock.System.now(),
            ),
        )
        scope.launch { syncRepository.syncPendingOperations() }
        return AppResult.Success(Unit)
    }

    override suspend fun updateChoreSchedule(choreId: String, schedule: ChoreSchedule?): EmptyResult {
        Timber.d("updateChoreSchedule: choreId=$choreId schedule=$schedule")
        updatePersonalChore(choreId) {
            it.copy(
                frequencyDays = schedule?.frequencyDays,
                scheduleWeekdays = (schedule as? ChoreSchedule.Weekdays)?.days.orEmpty(),
                scheduleMonthDay = (schedule as? ChoreSchedule.MonthDay)?.day,
            )
        }?.let { return it }
        editError(choreId)?.let { return AppResult.Error(it) }
        choreDao.updateSchedule(
            choreId = choreId,
            frequencyDays = schedule?.frequencyDays,
            weekdays = (schedule as? ChoreSchedule.Weekdays)?.days.orEmpty(),
            monthDay = (schedule as? ChoreSchedule.MonthDay)?.day,
        )
        pendingSyncOperationDao.upsert(
            PendingSyncOperationEntity(
                id = UUID.randomUUID().toString(),
                entityType = "chore",
                entityId = choreId,
                operationType = "update_schedule",
                payload = schedule?.toString().orEmpty(),
                createdAt = Clock.System.now(),
            ),
        )
        scope.launch { syncRepository.syncPendingOperations() }
        return AppResult.Success(Unit)
    }

    override suspend fun updateChoreCategory(choreId: String, category: ChoreCategory): EmptyResult {
        Timber.d("updateChoreCategory: choreId=$choreId category=$category")
        updatePersonalChore(choreId) { it.copy(category = category.name) }?.let { return it }
        editError(choreId)?.let { return AppResult.Error(it) }
        choreDao.updateCategory(choreId, category.name)
        pendingSyncOperationDao.upsert(
            PendingSyncOperationEntity(
                id = UUID.randomUUID().toString(),
                entityType = "chore",
                entityId = choreId,
                operationType = "update_category",
                payload = category.name,
                createdAt = Clock.System.now(),
            ),
        )
        scope.launch { syncRepository.syncPendingOperations() }
        return AppResult.Success(Unit)
    }

    override suspend fun updateChoreGroup(choreId: String, groupName: String?): EmptyResult {
        Timber.d("updateChoreGroup: choreId=$choreId groupName=$groupName")
        editError(choreId)?.let { return AppResult.Error(it) }
        val normalized = groupName.normalizedGroupName()
        choreDao.updateGroup(choreId, normalized)
        pendingSyncOperationDao.upsert(
            PendingSyncOperationEntity(
                id = UUID.randomUUID().toString(),
                entityType = "chore",
                entityId = choreId,
                operationType = "update_group",
                payload = normalized.orEmpty(),
                createdAt = Clock.System.now(),
            ),
        )
        scope.launch { syncRepository.syncPendingOperations() }
        return AppResult.Success(Unit)
    }

    override suspend fun updateChoreRotation(choreId: String, memberIds: List<String>): EmptyResult {
        Timber.d("updateChoreRotation: choreId=$choreId memberIds=$memberIds")
        editError(choreId)?.let { return AppResult.Error(it) }
        choreDao.updateRotation(choreId, memberIds, Clock.System.now().takeIf { memberIds.isNotEmpty() })
        pendingSyncOperationDao.upsert(
            PendingSyncOperationEntity(
                id = UUID.randomUUID().toString(),
                entityType = "chore",
                entityId = choreId,
                operationType = "update_rotation",
                payload = memberIds.joinToString(","),
                createdAt = Clock.System.now(),
            ),
        )
        scope.launch { syncRepository.syncPendingOperations() }
        return AppResult.Success(Unit)
    }

    // Null when choreId isn't the signed-in user's personal chore. Its owner may change it, so there's no permission
    // check.
    private suspend fun updatePersonalChore(
        choreId: String,
        change: (PersonalChoreEntity) -> PersonalChoreEntity,
    ): EmptyResult? {
        val chore = currentUserId()?.let { personalChoreDao.getChore(choreId, it) } ?: return null
        personalChoreDao.upsertChore(change(chore))
        queuePersonalChore(choreId)
        return AppResult.Success(Unit)
    }

    private suspend fun queuePersonalChore(choreId: String) {
        pendingSyncOperationDao.upsert(
            PendingSyncOperationEntity(
                id = UUID.randomUUID().toString(),
                entityType = "personal_chore",
                entityId = choreId,
                operationType = "upsert",
                payload = "",
                createdAt = Clock.System.now(),
            ),
        )
        scope.launch { syncRepository.syncPendingOperations() }
    }

    private fun String?.normalizedGroupName(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private suspend fun currentUserId(): String? =
        (authRepository.authState.first() as? AuthState.Authenticated)?.user?.id

    private suspend fun editError(choreId: String): AppError? {
        val chore = choreDao.getChore(choreId) ?: return AppError.CHORE_EDIT_FORBIDDEN
        return permissionError(chore, deleting = false)
    }

    private suspend fun permissionError(chore: ChoreEntity, deleting: Boolean): AppError? {
        val userId = currentUserId() ?: return AppError.NOT_SIGNED_IN
        val membership = memberDao.findByUserId(chore.householdId, userId)
            ?: return AppError.OWN_MEMBERSHIP_NOT_FOUND
        if (membership.removedAt != null) return AppError.OWN_MEMBERSHIP_NOT_FOUND
        val role = membership.role
        if (role == HouseholdRole.OWNER.name || role == HouseholdRole.ADMIN.name) return null
        if (chore.createdByUserId != userId) {
            return if (deleting) AppError.CHORE_DELETE_FORBIDDEN else AppError.CHORE_EDIT_FORBIDDEN
        }
        if (deleting && completionDao.getCompletions(chore.householdId).any { it.choreId == chore.id }) {
            return AppError.CHORE_DELETE_FORBIDDEN
        }
        return null
    }
}
