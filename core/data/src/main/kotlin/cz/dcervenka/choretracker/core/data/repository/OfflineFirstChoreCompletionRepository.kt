package cz.dcervenka.choretracker.core.data.repository

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.ChoreCompletionRepository
import cz.dcervenka.choretracker.core.data.contract.SyncRepository
import cz.dcervenka.choretracker.core.data.di.ApplicationScope
import cz.dcervenka.choretracker.core.database.dao.ChoreDao
import cz.dcervenka.choretracker.core.database.dao.CompletionDao
import cz.dcervenka.choretracker.core.database.dao.CompletionParticipantDao
import cz.dcervenka.choretracker.core.database.dao.HouseholdDao
import cz.dcervenka.choretracker.core.database.dao.MemberDao
import cz.dcervenka.choretracker.core.database.dao.PendingSyncOperationDao
import cz.dcervenka.choretracker.core.database.entity.CompletionEntity
import cz.dcervenka.choretracker.core.database.entity.CompletionParticipantEntity
import cz.dcervenka.choretracker.core.database.entity.PendingSyncOperationEntity
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.model.stats.RecentCompletion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Instant

@Singleton
class OfflineFirstChoreCompletionRepository @Inject constructor(
    private val completionDao: CompletionDao,
    private val participantDao: CompletionParticipantDao,
    private val choreDao: ChoreDao,
    private val memberDao: MemberDao,
    private val householdDao: HouseholdDao,
    private val pendingSyncOperationDao: PendingSyncOperationDao,
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : ChoreCompletionRepository {

    override fun observeRecentCompletions(householdId: String, limit: Int): Flow<List<RecentCompletion>> =
        combine(
            completionDao.observeRecentCompletions(householdId, limit),
            choreDao.observeChores(householdId),
            memberDao.observeMembers(householdId),
            participantDao.observeParticipants(householdId),
        ) { completions, chores, members, participants ->
            val choreMap = chores.associateBy { it.id }
            val memberMap = members.associateBy { it.id }
            val participantsByCompletion = participants.groupBy { it.completionId }
            completions.map { completion ->
                val completionParticipants = participantsByCompletion[completion.id].orEmpty()
                RecentCompletion(
                    completionId = completion.id,
                    choreName = choreMap[completion.choreId]?.name.orEmpty(),
                    note = completion.note,
                    completedAt = completion.createdAt,
                    participantNames = completionParticipants
                        .mapNotNull { memberMap[it.memberId]?.displayName },
                    participantMemberIds = completionParticipants.map { it.memberId },
                )
            }
        }

    override fun observeCompletionsByChore(householdId: String, choreId: String): Flow<List<RecentCompletion>> =
        combine(
            completionDao.observeCompletionsByChore(householdId, choreId),
            choreDao.observeChores(householdId),
            memberDao.observeMembers(householdId),
            participantDao.observeParticipants(householdId),
        ) { completions, chores, members, participants ->
            val choreMap = chores.associateBy { it.id }
            val memberMap = members.associateBy { it.id }
            val participantsByCompletion = participants.groupBy { it.completionId }
            completions.map { completion ->
                val completionParticipants = participantsByCompletion[completion.id].orEmpty()
                RecentCompletion(
                    completionId = completion.id,
                    choreName = choreMap[completion.choreId]?.name.orEmpty(),
                    note = completion.note,
                    completedAt = completion.createdAt,
                    participantNames = completionParticipants
                        .mapNotNull { memberMap[it.memberId]?.displayName },
                    participantMemberIds = completionParticipants.map { it.memberId },
                )
            }
        }

    override suspend fun logCompletion(
        householdId: String,
        choreId: String,
        participantMemberIds: List<String>,
        note: String?,
        completedAt: Instant?,
    ): AppResult<String> {
        Timber.d(
            "logCompletion: choreId=$choreId participants=${participantMemberIds.size} " +
                "hasNote=${note != null} completedAt=$completedAt",
        )
        val currentUserId = (authRepository.authState.first() as? AuthState.Authenticated)?.user?.id
            ?: return AppResult.Error("Sign in or continue in preview mode first.").also {
                Timber.w("logCompletion failed: user not authenticated")
            }
        val completionId = UUID.randomUUID().toString()
        completionDao.upsert(
            CompletionEntity(
                id = completionId,
                householdId = householdId,
                choreId = choreId,
                createdAt = completedAt ?: Clock.System.now(),
                createdByUserId = currentUserId,
                note = note?.takeIf(String::isNotBlank),
            ),
        )
        participantDao.insertAll(
            participantMemberIds.distinct().map { memberId ->
                CompletionParticipantEntity(
                    completionId = completionId,
                    memberId = memberId,
                )
            },
        )
        pendingSyncOperationDao.upsert(
            PendingSyncOperationEntity(
                id = UUID.randomUUID().toString(),
                entityType = "completion",
                entityId = completionId,
                operationType = "upsert",
                payload = note.orEmpty(),
                createdAt = Clock.System.now(),
            ),
        )
        scope.launch { syncRepository.syncPendingOperations() }
        return AppResult.Success(completionId)
    }

    override suspend fun updateCompletion(
        completionId: String,
        note: String?,
        participantMemberIds: List<String>,
    ): EmptyResult {
        Timber.d("updateCompletion: completionId=$completionId participants=${participantMemberIds.size}")
        val existing = completionDao.getCompletion(completionId)
        val currentUserId = (authRepository.authState.first() as? AuthState.Authenticated)?.user?.id
        return when {
            existing == null -> AppResult.Error("Completion not found")
            currentUserId == null -> AppResult.Error("Sign in or continue in preview mode first.")
            !canModifyCompletion(existing, currentUserId) -> {
                Timber.w("updateCompletion failed: userId=$currentUserId is not the owner or the author")
                AppResult.Error("Only the household owner or the person who logged this can edit it.")
            }
            else -> {
                completionDao.upsert(existing.copy(note = note?.takeIf(String::isNotBlank)))
                participantDao.deleteByCompletionId(completionId)
                participantDao.insertAll(
                    participantMemberIds.distinct().map { memberId ->
                        CompletionParticipantEntity(completionId = completionId, memberId = memberId)
                    },
                )
                pendingSyncOperationDao.upsert(
                    PendingSyncOperationEntity(
                        id = UUID.randomUUID().toString(),
                        entityType = "completion",
                        entityId = completionId,
                        operationType = "upsert",
                        payload = note.orEmpty(),
                        createdAt = Clock.System.now(),
                    ),
                )
                scope.launch { syncRepository.syncPendingOperations() }
                AppResult.Success(Unit)
            }
        }
    }

    override suspend fun deleteCompletion(completionId: String): EmptyResult {
        Timber.d("deleteCompletion: completionId=$completionId")
        val existing = completionDao.getCompletion(completionId)
        val currentUserId = (authRepository.authState.first() as? AuthState.Authenticated)?.user?.id
        return when {
            existing == null -> AppResult.Success(Unit)
            currentUserId == null -> AppResult.Error("Sign in or continue in preview mode first.")
            !canModifyCompletion(existing, currentUserId) -> {
                Timber.w("deleteCompletion failed: userId=$currentUserId is not the owner or the author")
                AppResult.Error("Only the household owner or the person who logged this can delete it.")
            }
            else -> {
                completionDao.deleteById(completionId)
                participantDao.deleteByCompletionId(completionId)
                pendingSyncOperationDao.deleteByEntityId(completionId)
                pendingSyncOperationDao.upsert(
                    PendingSyncOperationEntity(
                        id = UUID.randomUUID().toString(),
                        entityType = "completion",
                        entityId = completionId,
                        operationType = "delete",
                        payload = existing.householdId,
                        createdAt = Clock.System.now(),
                    ),
                )
                scope.launch { syncRepository.syncPendingOperations() }
                AppResult.Success(Unit)
            }
        }
    }

    // Mirrors firestore.rules' completions update/delete rule: the household owner can modify
    // any completion; a regular member can only modify one they authored themselves.
    private suspend fun canModifyCompletion(completion: CompletionEntity, currentUserId: String): Boolean {
        if (completion.createdByUserId == currentUserId) return true
        return householdDao.getHousehold(completion.householdId)?.ownerUserId == currentUserId
    }
}
