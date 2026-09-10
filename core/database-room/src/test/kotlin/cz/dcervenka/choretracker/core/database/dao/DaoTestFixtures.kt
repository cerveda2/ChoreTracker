package cz.dcervenka.choretracker.core.database.dao

import cz.dcervenka.choretracker.core.database.entity.ChoreEntity
import cz.dcervenka.choretracker.core.database.entity.CompletionEntity
import cz.dcervenka.choretracker.core.database.entity.CompletionParticipantEntity
import cz.dcervenka.choretracker.core.database.entity.HouseholdEntity
import cz.dcervenka.choretracker.core.database.entity.InviteEntity
import cz.dcervenka.choretracker.core.database.entity.MemberEntity
import cz.dcervenka.choretracker.core.database.entity.PendingSyncOperationEntity
import cz.dcervenka.choretracker.core.database.entity.SyncStateEntity
import kotlin.time.Instant

// Never defaults createdAt to Clock.System.now() - several DAOs ORDER BY createdAt, so
// same-millisecond entities would make ordering assertions flaky. Callers pass a deterministic
// instant (e.g. instantAt(1), instantAt(2), ...) whenever ordering matters.
fun instantAt(seconds: Long): Instant = Instant.fromEpochSeconds(seconds)

fun choreEntity(
    id: String,
    householdId: String = "household-1",
    name: String = "Chore $id",
    isActive: Boolean = true,
    createdAt: Instant = instantAt(1),
    deletedAt: Instant? = null,
    frequencyDays: Int? = 7,
    category: String = "OTHER",
) = ChoreEntity(
    id = id,
    householdId = householdId,
    name = name,
    isActive = isActive,
    createdAt = createdAt,
    deletedAt = deletedAt,
    frequencyDays = frequencyDays,
    category = category,
)

fun completionEntity(
    id: String,
    householdId: String = "household-1",
    choreId: String = "chore-1",
    createdAt: Instant = instantAt(1),
    createdByUserId: String = "user-1",
    note: String? = null,
) = CompletionEntity(
    id = id,
    householdId = householdId,
    choreId = choreId,
    createdAt = createdAt,
    createdByUserId = createdByUserId,
    note = note,
)

fun completionParticipantEntity(
    completionId: String,
    memberId: String,
) = CompletionParticipantEntity(completionId = completionId, memberId = memberId)

fun householdEntity(
    id: String,
    name: String = "Household $id",
    ownerUserId: String = "user-1",
    inviteCode: String = "CODE${id.uppercase()}",
    createdAt: Instant = instantAt(1),
) = HouseholdEntity(
    id = id,
    name = name,
    ownerUserId = ownerUserId,
    inviteCode = inviteCode,
    createdAt = createdAt,
)

fun inviteEntity(
    id: String,
    householdId: String = "household-1",
    code: String = "CODE$id",
    createdAt: Instant = instantAt(1),
    consumedAt: Instant? = null,
    targetMemberId: String? = null,
    consumedByMemberId: String? = null,
) = InviteEntity(
    id = id,
    householdId = householdId,
    code = code,
    createdAt = createdAt,
    consumedAt = consumedAt,
    targetMemberId = targetMemberId,
    consumedByMemberId = consumedByMemberId,
)

fun memberEntity(
    id: String,
    householdId: String = "household-1",
    userId: String? = "user-$id",
    displayName: String = "Member $id",
    role: String = "MEMBER",
    isCurrentUser: Boolean = false,
    email: String? = null,
    joinedViaInviteId: String? = null,
    removedAt: Instant? = null,
) = MemberEntity(
    id = id,
    householdId = householdId,
    userId = userId,
    displayName = displayName,
    role = role,
    isCurrentUser = isCurrentUser,
    email = email,
    joinedViaInviteId = joinedViaInviteId,
    removedAt = removedAt,
)

fun pendingSyncOperationEntity(
    id: String,
    entityType: String = "CHORE",
    entityId: String = "entity-1",
    operationType: String = "UPSERT",
    payload: String = "{}",
    createdAt: Instant = instantAt(1),
) = PendingSyncOperationEntity(
    id = id,
    entityType = entityType,
    entityId = entityId,
    operationType = operationType,
    payload = payload,
    createdAt = createdAt,
)

fun syncStateEntity(
    householdId: String,
    lastSyncedAt: Instant? = instantAt(1),
    lastSyncAttemptAt: Instant? = instantAt(1),
    pendingOperations: Int = 0,
    lastErrorMessage: String? = null,
) = SyncStateEntity(
    householdId = householdId,
    lastSyncedAt = lastSyncedAt,
    lastSyncAttemptAt = lastSyncAttemptAt,
    pendingOperations = pendingOperations,
    lastErrorMessage = lastErrorMessage,
)
