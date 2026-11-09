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
import cz.dcervenka.choretracker.core.database.entity.ChoreEntity
import cz.dcervenka.choretracker.core.database.entity.PendingSyncOperationEntity
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
import cz.dcervenka.choretracker.core.model.error.AppError
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

@Singleton
@Suppress("ReturnCount")
class OfflineFirstChoreRepository @Inject constructor(
    private val choreDao: ChoreDao,
    private val completionDao: CompletionDao,
    private val memberDao: MemberDao,
    private val authRepository: AuthRepository,
    private val pendingSyncOperationDao: PendingSyncOperationDao,
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

    override suspend fun addChore(
        householdId: String,
        name: String,
        category: ChoreCategory,
        frequencyDays: Int?,
        rotationMemberIds: List<String>,
    ): AppResult<String> {
        Timber.d("addChore: householdId=$householdId name=$name category=$category frequencyDays=$frequencyDays")
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
                frequencyDays = frequencyDays,
                category = category.name,
                createdByUserId = userId,
                rotationMemberIds = rotationMemberIds,
                rotationStartedAt = now.takeIf { rotationMemberIds.isNotEmpty() },
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

    override suspend fun updateChoreFrequencyDays(choreId: String, frequencyDays: Int?): EmptyResult {
        Timber.d("updateChoreFrequencyDays: choreId=$choreId frequencyDays=$frequencyDays")
        editError(choreId)?.let { return AppResult.Error(it) }
        choreDao.updateFrequencyDays(choreId, frequencyDays)
        pendingSyncOperationDao.upsert(
            PendingSyncOperationEntity(
                id = UUID.randomUUID().toString(),
                entityType = "chore",
                entityId = choreId,
                operationType = "update_frequency",
                payload = frequencyDays?.toString().orEmpty(),
                createdAt = Clock.System.now(),
            ),
        )
        scope.launch { syncRepository.syncPendingOperations() }
        return AppResult.Success(Unit)
    }

    override suspend fun updateChoreCategory(choreId: String, category: ChoreCategory): EmptyResult {
        Timber.d("updateChoreCategory: choreId=$choreId category=$category")
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
