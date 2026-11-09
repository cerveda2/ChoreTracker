package cz.dcervenka.choretracker.core.data.repository

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.ChoreRequestRepository
import cz.dcervenka.choretracker.core.data.contract.SyncRepository
import cz.dcervenka.choretracker.core.data.di.ApplicationScope
import cz.dcervenka.choretracker.core.data.mapper.asModel
import cz.dcervenka.choretracker.core.database.dao.ChoreRequestDao
import cz.dcervenka.choretracker.core.database.dao.MemberDao
import cz.dcervenka.choretracker.core.database.dao.PendingSyncOperationDao
import cz.dcervenka.choretracker.core.database.entity.ChoreRequestEntity
import cz.dcervenka.choretracker.core.database.entity.PendingSyncOperationEntity
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.model.chore.ChoreRequest
import cz.dcervenka.choretracker.core.model.chore.ChoreRequestState
import cz.dcervenka.choretracker.core.model.error.AppError
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
class OfflineFirstChoreRequestRepository @Inject constructor(
    private val choreRequestDao: ChoreRequestDao,
    private val memberDao: MemberDao,
    private val pendingSyncOperationDao: PendingSyncOperationDao,
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : ChoreRequestRepository {

    override fun observeRequests(householdId: String): Flow<List<ChoreRequest>> =
        choreRequestDao.observeRequests(householdId).map { requests -> requests.map(ChoreRequestEntity::asModel) }

    override suspend fun sendRequests(
        householdId: String,
        toMemberId: String,
        choreIds: List<String>,
        note: String?,
    ): EmptyResult {
        Timber.d("sendRequests: householdId=$householdId toMemberId=$toMemberId choreIds=$choreIds")
        val userId = currentUserId() ?: return AppResult.Error(AppError.NOT_SIGNED_IN)
        val sender = memberDao.findByUserId(householdId, userId)?.takeIf { it.removedAt == null }
            ?: return AppResult.Error(AppError.OWN_MEMBERSHIP_NOT_FOUND)
        val recipient = memberDao.findById(householdId, toMemberId)
            ?.takeIf { it.removedAt == null && it.id != sender.id }
            ?: return AppResult.Error(AppError.MEMBER_NOT_FOUND)
        // A placeholder has no account to notify or to decline with.
        val recipientUserId = recipient.userId ?: return AppResult.Error(AppError.MEMBER_NOT_JOINED)
        val batchId = UUID.randomUUID().toString()
        val now = Clock.System.now()
        choreRequestDao.insertBatch(
            requests = choreIds.distinct().map { choreId ->
                ChoreRequestEntity(
                    id = UUID.randomUUID().toString(),
                    householdId = householdId,
                    batchId = batchId,
                    choreId = choreId,
                    fromMemberId = sender.id,
                    toMemberId = recipient.id,
                    toUserId = recipientUserId,
                    createdByUserId = userId,
                    note = note?.trim()?.takeIf { it.isNotEmpty() },
                    createdAt = now,
                    state = ChoreRequestState.OPEN.name,
                    resolvedAt = null,
                )
            },
            operation = PendingSyncOperationEntity(
                id = UUID.randomUUID().toString(),
                entityType = "request",
                entityId = batchId,
                operationType = "send",
                payload = householdId,
                createdAt = now,
            ),
        )
        scope.launch { syncRepository.syncPendingOperations() }
        return AppResult.Success(Unit)
    }

    override suspend fun cancelRequest(requestId: String): EmptyResult =
        resolve(requestId, ChoreRequestState.CANCELLED) { it.createdByUserId }

    override suspend fun declineRequest(requestId: String): EmptyResult =
        resolve(requestId, ChoreRequestState.DECLINED) { it.toUserId }

    private suspend fun resolve(
        requestId: String,
        state: ChoreRequestState,
        allowedUserId: (ChoreRequestEntity) -> String,
    ): EmptyResult {
        Timber.d("resolveRequest: requestId=$requestId state=$state")
        val userId = currentUserId() ?: return AppResult.Error(AppError.NOT_SIGNED_IN)
        val request = choreRequestDao.getRequest(requestId)
        return when {
            request == null || request.state != ChoreRequestState.OPEN.name -> AppResult.Success(Unit)
            allowedUserId(request) != userId -> AppResult.Error(AppError.PERMISSION_DENIED)
            else -> {
                val now = Clock.System.now()
                choreRequestDao.updateState(requestId, state.name, now)
                pendingSyncOperationDao.upsert(
                    PendingSyncOperationEntity(
                        id = UUID.randomUUID().toString(),
                        entityType = "request",
                        entityId = requestId,
                        operationType = "resolve",
                        payload = request.householdId,
                        createdAt = now,
                    ),
                )
                scope.launch { syncRepository.syncPendingOperations() }
                AppResult.Success(Unit)
            }
        }
    }

    private suspend fun currentUserId(): String? =
        (authRepository.authState.first() as? AuthState.Authenticated)?.user?.id
}
