package cz.dcervenka.choretracker.core.data.repository

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.ChoreRepository
import cz.dcervenka.choretracker.core.data.contract.SyncRepository
import cz.dcervenka.choretracker.core.data.mapper.asModel
import cz.dcervenka.choretracker.core.database.dao.ChoreDao
import cz.dcervenka.choretracker.core.database.dao.PendingSyncOperationDao
import cz.dcervenka.choretracker.core.database.entity.ChoreEntity
import cz.dcervenka.choretracker.core.database.entity.PendingSyncOperationEntity
import cz.dcervenka.choretracker.core.model.chore.Chore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

@Singleton
class OfflineFirstChoreRepository @Inject constructor(
    private val choreDao: ChoreDao,
    private val pendingSyncOperationDao: PendingSyncOperationDao,
    private val syncRepository: SyncRepository,
) : ChoreRepository {

    override fun observeChores(householdId: String): Flow<List<Chore>> =
        choreDao.observeChores(householdId).map { chores -> chores.map(ChoreEntity::asModel) }

    override suspend fun addChore(householdId: String, name: String): EmptyResult {
        val choreId = UUID.randomUUID().toString()
        choreDao.upsert(
            ChoreEntity(
                id = choreId,
                householdId = householdId,
                name = name,
                isActive = true,
                createdAt = Clock.System.now(),
                deletedAt = null,
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
        syncRepository.syncPendingOperations()
        return AppResult.Success(Unit)
    }

    override suspend fun deleteChore(choreId: String): EmptyResult {
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
        syncRepository.syncPendingOperations()
        return AppResult.Success(Unit)
    }

    override suspend fun updateChoreActive(choreId: String, isActive: Boolean): EmptyResult {
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
        syncRepository.syncPendingOperations()
        return AppResult.Success(Unit)
    }

    override suspend fun updateChoreName(choreId: String, name: String): EmptyResult {
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
        syncRepository.syncPendingOperations()
        return AppResult.Success(Unit)
    }

    override suspend fun updateChoreFrequencyDays(choreId: String, frequencyDays: Int?): EmptyResult {
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
        syncRepository.syncPendingOperations()
        return AppResult.Success(Unit)
    }
}
