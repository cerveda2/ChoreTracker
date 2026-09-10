package cz.dcervenka.choretracker.core.sync.repository

import cz.dcervenka.choretracker.core.database.dao.ChoreDao
import cz.dcervenka.choretracker.core.database.dao.CompletionDao
import cz.dcervenka.choretracker.core.database.dao.CompletionParticipantDao
import cz.dcervenka.choretracker.core.database.dao.InviteDao
import cz.dcervenka.choretracker.core.database.dao.MemberDao
import cz.dcervenka.choretracker.core.database.dao.PendingSyncOperationDao
import cz.dcervenka.choretracker.core.database.database.ChoreTrackerDatabase
import cz.dcervenka.choretracker.core.database.entity.ChoreEntity
import cz.dcervenka.choretracker.core.database.entity.CompletionEntity
import cz.dcervenka.choretracker.core.database.entity.CompletionParticipantEntity
import cz.dcervenka.choretracker.core.database.entity.InviteEntity
import cz.dcervenka.choretracker.core.database.entity.MemberEntity
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.chore.ChoreCompletion
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.household.Invite
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Applies snapshots from LocalSyncRepository's real-time Firestore listeners
 * (observeRealtimeUpdates) onto Room. Split out from LocalSyncRepository so that class stays
 * under detekt's LargeClass threshold - this is the real-time counterpart to that class's
 * push/pull sync (applySnapshot, syncPendingOperations), which it does not touch.
 */
internal class RealtimeSyncApplier(
    private val memberDao: MemberDao,
    private val choreDao: ChoreDao,
    private val completionDao: CompletionDao,
    private val completionParticipantDao: CompletionParticipantDao,
    private val inviteDao: InviteDao,
    private val pendingSyncOperationDao: PendingSyncOperationDao,
    private val database: ChoreTrackerDatabase,
) {

    // observeRealtimeUpdates' merge() collects all four remote listeners concurrently, so these
    // methods can be invoked from different coroutines at the same time - e.g. a members update
    // triggering clearAll() while a completions update is mid-upsert. Serializing them avoids
    // applying a partial write after (or racing) a full local wipe.
    private val mutex = Mutex()

    suspend fun applyMembers(householdId: String, userId: String, members: List<HouseholdMember>) =
        mutex.withLock {
            // removedAt == null, not just presence: a soft-removed member's doc stays in the
            // collection, so "I'm still listed" no longer means "I still have access" - this is
            // the client side of the removedAt/active enforcement (see PR #80's clearAll path).
            if (members.none { it.userId == userId && it.removedAt == null }) {
                Timber.w(
                    "applyMembers: userId=$userId no longer an active member of household=$householdId - " +
                        "clearing local data",
                )
                database.clearAll()
                return@withLock
            }
            // Pending member operations are keyed by householdId, not their own member id (see
            // enqueueOperation call sites in OfflineFirstHouseholdRepository), so there's no way
            // to tell which member a pending op is actually about - a household with any pending
            // member operation skips this reconciliation entirely (both the upsert below and the
            // prune) rather than risk a stale snapshot clobbering a not-yet-pushed local change,
            // e.g. reverting a just-set removedAt back to null, or deleting a just-added local
            // member row, ahead of an unrelated snapshot for the same household arriving first.
            val hasPendingMemberOps = pendingSyncOperationDao.getAll()
                .any { it.entityType == "member" && it.entityId == householdId }
            if (hasPendingMemberOps) {
                return@withLock
            }
            deduplicateMembers(members).forEach { member ->
                memberDao.upsert(
                    MemberEntity(
                        id = member.id,
                        householdId = member.householdId,
                        userId = member.userId,
                        displayName = member.displayName,
                        role = member.role.name,
                        isCurrentUser = member.isCurrentUser,
                        email = member.email,
                        joinedViaInviteId = member.joinedViaInviteId,
                        removedAt = member.removedAt,
                    ),
                )
            }
            // No prune step for soft-removed members: they stay present in the remote members
            // collection (removedAt/active only), so they're never "absent from the listener" -
            // same as chores' soft delete (see applyChores). This still handles a member row that
            // genuinely vanished remotely for some other reason.
            val memberIds = members.map { it.id }.toSet()
            memberDao.getMembers(householdId)
                .filter { it.id !in memberIds }
                .forEach { memberDao.deleteById(it.id) }
        }

    suspend fun applyCompletions(householdId: String, completions: List<ChoreCompletion>) = mutex.withLock {
        completions.forEach { completion ->
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
            completionParticipantDao.deleteByCompletionId(completion.id)
            completionParticipantDao.insertAll(
                completion.participantMemberIds.map { memberId ->
                    CompletionParticipantEntity(
                        completionId = completion.id,
                        memberId = memberId,
                    )
                },
            )
        }
        // Completions are enqueued with their own id as entityId (see
        // OfflineFirstChoreCompletionRepository), so a not-yet-synced completion is excluded
        // individually rather than skipping reconciliation for the whole household.
        val pendingCompletionIds = pendingSyncOperationDao.getAll()
            .filter { it.entityType == "completion" }
            .map { it.entityId }
            .toSet()
        val completionIds = completions.map { it.id }.toSet()
        completionDao.getCompletions(householdId)
            .filter { it.id !in completionIds && it.id !in pendingCompletionIds }
            .forEach { completionDao.deleteById(it.id) }
    }

    suspend fun applyInvites(householdId: String, invites: List<Invite>) = mutex.withLock {
        invites.forEach { invite ->
            inviteDao.upsert(
                InviteEntity(
                    id = invite.id,
                    householdId = invite.householdId,
                    code = invite.code,
                    createdAt = invite.createdAt,
                    consumedAt = invite.consumedAt,
                    targetMemberId = invite.targetMemberId,
                    consumedByMemberId = invite.consumedByMemberId,
                ),
            )
        }
        // Pending invite operations are keyed by householdId, not their own invite id (see
        // enqueueOperation call sites in OfflineFirstHouseholdRepository) - same reasoning as
        // applyMembers above.
        val hasPendingInviteOps = pendingSyncOperationDao.getAll()
            .any { it.entityType == "invite" && it.entityId == householdId }
        if (!hasPendingInviteOps) {
            val inviteIds = invites.map { it.id }.toSet()
            inviteDao.getInvites(householdId)
                .filter { it.id !in inviteIds }
                .forEach { inviteDao.deleteById(it.id) }
        }
    }

    // No prune step: chores are soft-deleted (isActive/deletedAt fields, see ChoreEntity), never
    // removed from Firestore - same as the existing pull-based sync (pruneStaleLocalRows doesn't
    // touch chores either), so there's nothing stale to reconcile here.
    suspend fun applyChores(chores: List<Chore>) = mutex.withLock {
        chores.forEach { chore ->
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
    }
}
