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
import cz.dcervenka.choretracker.core.model.auth.AppUser
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

// Matches firestore.rules' note limit, which keeps the push under FCM's 4 KB.
private const val MAX_NOTE_LENGTH = 500

// firestore.rules turns down a batched write of 18 requests (20 reads per batch); 10 leaves room.
private const val MAX_REQUESTS_PER_BATCH = 10

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
        val user = currentUser() ?: return AppResult.Error(AppError.NOT_SIGNED_IN)
        if (user.isPreview) return AppResult.Error(AppError.PREVIEW_MODE_RESTRICTED)
        val distinctChoreIds = choreIds.distinct()
        if (distinctChoreIds.isEmpty()) return AppResult.Error(AppError.CHORE_REQUIRED)
        val sender = memberDao.findByUserId(householdId, user.id)?.takeIf { it.removedAt == null }
            ?: return AppResult.Error(AppError.OWN_MEMBERSHIP_NOT_FOUND)
        val recipient = memberDao.findById(householdId, toMemberId)
            ?.takeIf { it.removedAt == null && it.id != sender.id }
            ?: return AppResult.Error(AppError.MEMBER_NOT_FOUND)
        // A placeholder has no account to notify or to decline with.
        val recipientUserId = recipient.userId ?: return AppResult.Error(AppError.MEMBER_NOT_JOINED)
        val now = Clock.System.now()
        val trimmedNote = note?.trim()?.take(MAX_NOTE_LENGTH)?.takeIf { it.isNotEmpty() }
        // Each request's create rule reads both member docs and its chore, and a batched write gets 20
        // reads in all, so a bigger ask goes out as several batches - one push each.
        val batches = distinctChoreIds.chunked(MAX_REQUESTS_PER_BATCH).map { chunk ->
            val batchId = UUID.randomUUID().toString()
            val requests = chunk.map { choreId ->
                ChoreRequestEntity(
                    id = UUID.randomUUID().toString(),
                    householdId = householdId,
                    batchId = batchId,
                    choreId = choreId,
                    fromMemberId = sender.id,
                    toMemberId = recipient.id,
                    toUserId = recipientUserId,
                    createdByUserId = user.id,
                    note = trimmedNote,
                    createdAt = now,
                    state = ChoreRequestState.OPEN.name,
                    resolvedAt = null,
                )
            }
            val operation = PendingSyncOperationEntity(
                id = UUID.randomUUID().toString(),
                entityType = "request",
                entityId = batchId,
                operationType = "send",
                payload = householdId,
                createdAt = now,
            )
            requests to operation
        }
        choreRequestDao.insertBatches(
            requests = batches.flatMap { (requests, _) -> requests },
            operations = batches.map { (_, operation) -> operation },
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
        val user = currentUser() ?: return AppResult.Error(AppError.NOT_SIGNED_IN)
        if (user.isPreview) return AppResult.Error(AppError.PREVIEW_MODE_RESTRICTED)
        val request = choreRequestDao.getRequest(requestId)
        return when {
            request == null || request.state != ChoreRequestState.OPEN.name -> AppResult.Success(Unit)
            allowedUserId(request) != user.id -> AppResult.Error(AppError.PERMISSION_DENIED)
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

    private suspend fun currentUser(): AppUser? = (authRepository.authState.first() as? AuthState.Authenticated)?.user
}
