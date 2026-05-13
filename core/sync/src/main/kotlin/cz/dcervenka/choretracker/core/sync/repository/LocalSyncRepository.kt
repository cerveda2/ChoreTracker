package cz.dcervenka.choretracker.core.sync.repository

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.SyncRepository
import cz.dcervenka.choretracker.core.database.dao.ChoreDao
import cz.dcervenka.choretracker.core.database.dao.CompletionDao
import cz.dcervenka.choretracker.core.database.dao.CompletionParticipantDao
import cz.dcervenka.choretracker.core.database.dao.HouseholdDao
import cz.dcervenka.choretracker.core.database.dao.InviteDao
import cz.dcervenka.choretracker.core.database.dao.MemberDao
import cz.dcervenka.choretracker.core.database.dao.PendingSyncOperationDao
import cz.dcervenka.choretracker.core.database.dao.SyncStateDao
import cz.dcervenka.choretracker.core.database.entity.ChoreEntity
import cz.dcervenka.choretracker.core.database.entity.CompletionEntity
import cz.dcervenka.choretracker.core.database.entity.CompletionParticipantEntity
import cz.dcervenka.choretracker.core.database.entity.HouseholdEntity
import cz.dcervenka.choretracker.core.database.entity.InviteEntity
import cz.dcervenka.choretracker.core.database.entity.MemberEntity
import cz.dcervenka.choretracker.core.database.entity.PendingSyncOperationEntity
import cz.dcervenka.choretracker.core.database.entity.SyncStateEntity
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
import cz.dcervenka.choretracker.core.model.chore.ChoreCompletion
import cz.dcervenka.choretracker.core.model.household.Household
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import cz.dcervenka.choretracker.core.model.household.Invite
import cz.dcervenka.choretracker.core.model.sync.HouseholdSnapshot
import cz.dcervenka.choretracker.core.model.sync.SyncState
import cz.dcervenka.choretracker.core.remote.contract.RemoteHouseholdDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

@Singleton
class LocalSyncRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val householdDao: HouseholdDao,
    private val memberDao: MemberDao,
    private val choreDao: ChoreDao,
    private val completionDao: CompletionDao,
    private val completionParticipantDao: CompletionParticipantDao,
    private val inviteDao: InviteDao,
    private val pendingSyncOperationDao: PendingSyncOperationDao,
    private val syncStateDao: SyncStateDao,
    private val remoteHouseholdDataSource: RemoteHouseholdDataSource,
) : SyncRepository {

    override fun observeSyncState(householdId: String): Flow<SyncState?> =
        syncStateDao.observeSyncState(householdId).map { state ->
            state?.let {
                SyncState(
                    householdId = it.householdId,
                    lastSyncedAt = it.lastSyncedAt,
                    lastSyncAttemptAt = it.lastSyncAttemptAt,
                    pendingOperations = it.pendingOperations,
                    lastErrorMessage = it.lastErrorMessage,
                )
            }
        }

    override suspend fun syncPendingOperations(): EmptyResult {
        val authenticatedUser = (authRepository.authState.first() as? AuthState.Authenticated)?.user
        val shouldSkipSync = authenticatedUser == null || authenticatedUser.isPreview
        if (shouldSkipSync) {
            return AppResult.Success(Unit)
        }

        val operations = pendingSyncOperationDao.getAll()
        Timber.d("syncPendingOperations: ${operations.size} pending operations")
        val operationIdsByHouseholdId = linkedMapOf<String, MutableList<String>>().apply {
            operations.forEach { operation ->
                resolveHouseholdId(operation)?.let { householdId ->
                    getOrPut(householdId) { mutableListOf() }.add(operation.id)
                }
            }
        }

        val syncError = if (operations.isEmpty()) {
            null
        } else {
            operationIdsByHouseholdId.entries.firstNotNullOfOrNull { (householdId, operationIds) ->
                val snapshot = buildSnapshot(householdId) ?: return@firstNotNullOfOrNull null
                val now = Clock.System.now()
                when (val result = remoteHouseholdDataSource.upsertHouseholdSnapshot(snapshot, authenticatedUser.id)) {
                    is AppResult.Error -> {
                        Timber.e("syncPendingOperations: sync failed for household=$householdId - ${result.message}")
                        syncStateDao.upsert(
                            SyncStateEntity(
                                householdId = householdId,
                                lastSyncedAt = null,
                                lastSyncAttemptAt = now,
                                pendingOperations = operationIds.size,
                                lastErrorMessage = result.message,
                            ),
                        )
                        AppResult.Error(result.message, result.cause)
                    }
                    is AppResult.Success -> {
                        Timber.d(
                            "syncPendingOperations: synced ${operationIds.size} operations for household=$householdId",
                        )
                        operationIds.forEach { operationId ->
                            pendingSyncOperationDao.delete(operationId)
                        }
                        syncStateDao.upsert(
                            SyncStateEntity(
                                householdId = householdId,
                                lastSyncedAt = now,
                                lastSyncAttemptAt = now,
                                pendingOperations = 0,
                                lastErrorMessage = null,
                            ),
                        )
                        null
                    }
                }
            }
        }

        return syncError ?: AppResult.Success(Unit)
    }

    override suspend fun restoreHouseholdForUser(userId: String): AppResult<Boolean> {
        Timber.d("restoreHouseholdForUser: userId=$userId")
        return when (val result = remoteHouseholdDataSource.fetchHouseholdSnapshot(userId)) {
            is AppResult.Error -> {
                Timber.e("restoreHouseholdForUser: fetch failed - ${result.message}")
                AppResult.Error(result.message, result.cause)
            }
            is AppResult.Success -> {
                val snapshot = result.value ?: return AppResult.Success(false).also {
                    Timber.d("restoreHouseholdForUser: no remote snapshot found")
                }
                householdDao.upsert(
                    HouseholdEntity(
                        id = snapshot.household.id,
                        name = snapshot.household.name,
                        ownerUserId = snapshot.household.ownerUserId,
                        inviteCode = snapshot.household.inviteCode,
                        createdAt = snapshot.household.createdAt,
                    ),
                )
                snapshot.members.forEach { member ->
                    memberDao.upsert(
                        MemberEntity(
                            id = member.id,
                            householdId = member.householdId,
                            userId = member.userId,
                            displayName = member.displayName,
                            role = member.role.name,
                            isCurrentUser = member.isCurrentUser,
                        ),
                    )
                }
                snapshot.chores.forEach { chore ->
                    choreDao.upsert(
                        ChoreEntity(
                            id = chore.id,
                            householdId = chore.householdId,
                            name = chore.name,
                            isActive = chore.isActive,
                            createdAt = chore.createdAt,
                            deletedAt = chore.deletedAt,
                            frequencyDays = chore.frequencyDays,
                            category = chore.category.name,
                        ),
                    )
                }
                snapshot.completions.forEach { completion ->
                    completionDao.upsert(
                        CompletionEntity(
                            id = completion.id,
                            householdId = completion.householdId,
                            choreId = completion.choreId,
                            createdAt = completion.createdAt,
                            createdByUserId = completion.createdByUserId,
                            note = completion.note,
                        ),
                    )
                    completionParticipantDao.insertAll(
                        completion.participantMemberIds.map { memberId ->
                            CompletionParticipantEntity(
                                completionId = completion.id,
                                memberId = memberId,
                            )
                        },
                    )
                }
                snapshot.invites.forEach { invite ->
                    inviteDao.upsert(
                        InviteEntity(
                            id = invite.id,
                            householdId = invite.householdId,
                            code = invite.code,
                            createdAt = invite.createdAt,
                            consumedAt = invite.consumedAt,
                        ),
                    )
                }
                Timber.d(
                    "restoreHouseholdForUser: restored household=${snapshot.household.id} " +
                        "members=${snapshot.members.size} chores=${snapshot.chores.size} completions=${snapshot.completions.size}",
                )
                AppResult.Success(true)
            }
        }
    }

    private suspend fun resolveHouseholdId(
        operation: PendingSyncOperationEntity,
    ): String? = when (operation.entityType) {
        "household", "member", "invite" -> operation.entityId
        "chore" -> choreDao.getChore(operation.entityId)?.householdId
        "completion" -> completionDao.getCompletion(operation.entityId)?.householdId
        else -> null
    }

    private suspend fun buildSnapshot(householdId: String): HouseholdSnapshot? {
        val household = householdDao.getHousehold(householdId) ?: return null
        val participants = completionParticipantDao.getParticipants(householdId)
        val members = memberDao.getMembers(householdId).map { member ->
            HouseholdMember(
                id = member.id,
                householdId = member.householdId,
                userId = member.userId,
                displayName = member.displayName,
                role = HouseholdRole.valueOf(member.role),
                isCurrentUser = member.isCurrentUser,
            )
        }
        val completions = completionDao.getCompletions(householdId).map { completion ->
            ChoreCompletion(
                id = completion.id,
                householdId = completion.householdId,
                choreId = completion.choreId,
                createdAt = completion.createdAt,
                createdByUserId = completion.createdByUserId,
                note = completion.note,
                participantMemberIds = participants
                    .filter { it.completionId == completion.id }
                    .map { it.memberId },
            )
        }
        return HouseholdSnapshot(
            household = Household(
                id = household.id,
                name = household.name,
                ownerUserId = household.ownerUserId,
                inviteCode = household.inviteCode,
                createdAt = household.createdAt,
            ),
            members = members,
            chores = choreDao.getChores(householdId).map { chore ->
                Chore(
                    id = chore.id,
                    householdId = chore.householdId,
                    name = chore.name,
                    isActive = chore.isActive,
                    createdAt = chore.createdAt,
                    deletedAt = chore.deletedAt,
                    frequencyDays = chore.frequencyDays,
                    category = runCatching { ChoreCategory.valueOf(chore.category) }.getOrDefault(ChoreCategory.OTHER),
                )
            },
            completions = completions,
            invites = inviteDao.getInvites(householdId).map { invite ->
                Invite(
                    id = invite.id,
                    householdId = invite.householdId,
                    code = invite.code,
                    createdAt = invite.createdAt,
                    consumedAt = invite.consumedAt,
                )
            },
        )
    }
}
