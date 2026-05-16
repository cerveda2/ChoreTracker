package cz.dcervenka.choretracker.core.data.mapper

import cz.dcervenka.choretracker.core.database.entity.ChoreEntity
import cz.dcervenka.choretracker.core.database.entity.CompletionEntity
import cz.dcervenka.choretracker.core.database.entity.CompletionParticipantEntity
import cz.dcervenka.choretracker.core.database.entity.HouseholdEntity
import cz.dcervenka.choretracker.core.database.entity.InviteEntity
import cz.dcervenka.choretracker.core.database.entity.MemberEntity
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
import cz.dcervenka.choretracker.core.model.chore.ChoreCompletion
import cz.dcervenka.choretracker.core.model.household.Household
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import cz.dcervenka.choretracker.core.model.household.Invite

internal fun HouseholdEntity.asModel(): Household = Household(
    id = id,
    name = name,
    ownerUserId = ownerUserId,
    inviteCode = inviteCode,
    createdAt = createdAt,
)

internal fun MemberEntity.asModel(): HouseholdMember = HouseholdMember(
    id = id,
    householdId = householdId,
    userId = userId,
    displayName = displayName,
    role = HouseholdRole.valueOf(role),
    isCurrentUser = isCurrentUser,
    email = email,
)

internal fun ChoreEntity.asModel(): Chore = Chore(
    id = id,
    householdId = householdId,
    name = name,
    isActive = isActive,
    createdAt = createdAt,
    deletedAt = deletedAt,
    frequencyDays = frequencyDays,
    category = runCatching { ChoreCategory.valueOf(category) }.getOrDefault(ChoreCategory.OTHER),
)

internal fun InviteEntity.asModel(): Invite = Invite(
    id = id,
    householdId = householdId,
    code = code,
    createdAt = createdAt,
    consumedAt = consumedAt,
    targetMemberId = targetMemberId,
)

internal fun List<CompletionEntity>.asModels(
    participants: List<CompletionParticipantEntity>,
): List<ChoreCompletion> {
    val participantIdsByCompletionId = participants.groupBy(CompletionParticipantEntity::completionId)
        .mapValues { (_, completionParticipants) ->
            completionParticipants.map(CompletionParticipantEntity::memberId)
        }
    return map { completion ->
        ChoreCompletion(
            id = completion.id,
            householdId = completion.householdId,
            choreId = completion.choreId,
            createdAt = completion.createdAt,
            createdByUserId = completion.createdByUserId,
            note = completion.note,
            participantMemberIds = participantIdsByCompletionId[completion.id].orEmpty(),
        )
    }
}
