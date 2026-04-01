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
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.model.chore.ChoreCompletion
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import cz.dcervenka.choretracker.core.model.sync.HouseholdSnapshot
import cz.dcervenka.choretracker.core.remote.contract.RemoteHouseholdDataSource
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

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
    private val remoteHouseholdDataSource: RemoteHouseholdDataSource,
) : SyncRepository {

    override suspend fun syncPendingOperations(): EmptyResult {
        val authenticatedUser = (authRepository.authState.first() as? AuthState.Authenticated)?.user
            ?: return AppResult.Success(Unit)
        if (authenticatedUser.isPreview) return AppResult.Success(Unit)

        val operations = pendingSyncOperationDao.getAll()
        if (operations.isEmpty()) return AppResult.Success(Unit)

        val operationIdsByHouseholdId = linkedMapOf<String, MutableList<String>>()
        operations.forEach { operation ->
            val householdId = resolveHouseholdId(operation) ?: return@forEach
            operationIdsByHouseholdId.getOrPut(householdId) { mutableListOf() }
                .add(operation.id)
        }

        for ((householdId, operationIds) in operationIdsByHouseholdId) {
            val snapshot = buildSnapshot(householdId) ?: continue
            when (val result = remoteHouseholdDataSource.upsertHouseholdSnapshot(snapshot, authenticatedUser.id)) {
                is AppResult.Error -> {
                    return AppResult.Error(
                        result.message,
                        result.cause,
                    )
                }
                is AppResult.Success -> operationIds.forEach { operationId ->
                    pendingSyncOperationDao.delete(operationId)
                }
            }
        }
        return AppResult.Success(Unit)
    }

    override suspend fun restoreHouseholdForUser(userId: String): AppResult<Boolean> = when (
        val result = remoteHouseholdDataSource.fetchHouseholdSnapshot(userId)
    ) {
        is AppResult.Error -> AppResult.Error(result.message, result.cause)
        is AppResult.Success -> {
            val snapshot = result.value ?: return AppResult.Success(false)
            householdDao.upsert(
                cz.dcervenka.choretracker.core.database.entity.HouseholdEntity(
                    id = snapshot.household.id,
                    name = snapshot.household.name,
                    ownerUserId = snapshot.household.ownerUserId,
                    inviteCode = snapshot.household.inviteCode,
                    createdAt = snapshot.household.createdAt,
                ),
            )
            snapshot.members.forEach { member ->
                memberDao.upsert(
                    cz.dcervenka.choretracker.core.database.entity.MemberEntity(
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
                    cz.dcervenka.choretracker.core.database.entity.ChoreEntity(
                        id = chore.id,
                        householdId = chore.householdId,
                        name = chore.name,
                        isActive = chore.isActive,
                        createdAt = chore.createdAt,
                        deletedAt = chore.deletedAt,
                    ),
                )
            }
            snapshot.completions.forEach { completion ->
                completionDao.upsert(
                    cz.dcervenka.choretracker.core.database.entity.CompletionEntity(
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
                        cz.dcervenka.choretracker.core.database.entity.CompletionParticipantEntity(
                            completionId = completion.id,
                            memberId = memberId,
                        )
                    },
                )
            }
            snapshot.invites.forEach { invite ->
                inviteDao.upsert(
                    cz.dcervenka.choretracker.core.database.entity.InviteEntity(
                        id = invite.id,
                        householdId = invite.householdId,
                        code = invite.code,
                        createdAt = invite.createdAt,
                        consumedAt = invite.consumedAt,
                    ),
                )
            }
            AppResult.Success(true)
        }
    }

    private suspend fun resolveHouseholdId(
        operation: cz.dcervenka.choretracker.core.database.entity.PendingSyncOperationEntity,
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
            household = cz.dcervenka.choretracker.core.model.household.Household(
                id = household.id,
                name = household.name,
                ownerUserId = household.ownerUserId,
                inviteCode = household.inviteCode,
                createdAt = household.createdAt,
            ),
            members = members,
            chores = choreDao.getChores(householdId).map { chore ->
                cz.dcervenka.choretracker.core.model.chore.Chore(
                    id = chore.id,
                    householdId = chore.householdId,
                    name = chore.name,
                    isActive = chore.isActive,
                    createdAt = chore.createdAt,
                    deletedAt = chore.deletedAt,
                )
            },
            completions = completions,
            invites = inviteDao.getInvites(householdId).map { invite ->
                cz.dcervenka.choretracker.core.model.household.Invite(
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
