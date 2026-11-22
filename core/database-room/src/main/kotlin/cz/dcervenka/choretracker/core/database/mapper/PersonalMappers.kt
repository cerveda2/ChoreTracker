package cz.dcervenka.choretracker.core.database.mapper

import cz.dcervenka.choretracker.core.database.entity.PersonalChoreEntity
import cz.dcervenka.choretracker.core.database.entity.PersonalCompletionEntity
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
import cz.dcervenka.choretracker.core.model.chore.ChoreCompletion

fun PersonalChoreEntity.asModel(): Chore = Chore(
    id = id,
    householdId = "",
    name = name,
    isActive = isActive,
    createdAt = createdAt,
    deletedAt = deletedAt,
    frequencyDays = frequencyDays,
    category = runCatching { ChoreCategory.valueOf(category) }.getOrDefault(ChoreCategory.OTHER),
    createdByUserId = ownerUserId,
    scheduleWeekdays = scheduleWeekdays,
    scheduleMonthDay = scheduleMonthDay,
    isPersonal = true,
)

fun Chore.asPersonalEntity(ownerUserId: String): PersonalChoreEntity = PersonalChoreEntity(
    id = id,
    ownerUserId = ownerUserId,
    name = name,
    isActive = isActive,
    createdAt = createdAt,
    deletedAt = deletedAt,
    frequencyDays = frequencyDays,
    category = category.name,
    scheduleWeekdays = scheduleWeekdays,
    scheduleMonthDay = scheduleMonthDay,
)

fun PersonalCompletionEntity.asModel(): ChoreCompletion = ChoreCompletion(
    id = id,
    householdId = "",
    choreId = choreId,
    createdAt = createdAt,
    createdByUserId = ownerUserId,
    note = note,
    participantMemberIds = emptyList(),
)

fun ChoreCompletion.asPersonalEntity(ownerUserId: String): PersonalCompletionEntity = PersonalCompletionEntity(
    id = id,
    ownerUserId = ownerUserId,
    choreId = choreId,
    createdAt = createdAt,
    note = note,
)
