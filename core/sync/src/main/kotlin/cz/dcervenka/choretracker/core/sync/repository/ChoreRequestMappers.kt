package cz.dcervenka.choretracker.core.sync.repository

import cz.dcervenka.choretracker.core.database.entity.ChoreRequestEntity
import cz.dcervenka.choretracker.core.model.chore.ChoreRequest
import cz.dcervenka.choretracker.core.model.chore.ChoreRequestState

internal fun ChoreRequest.asEntity(): ChoreRequestEntity = ChoreRequestEntity(
    id = id,
    householdId = householdId,
    batchId = batchId,
    choreId = choreId,
    fromMemberId = fromMemberId,
    toMemberId = toMemberId,
    toUserId = toUserId,
    createdByUserId = createdByUserId,
    note = note,
    createdAt = createdAt,
    state = state.name,
    resolvedAt = resolvedAt,
)

internal fun ChoreRequestEntity.asRequestModel(): ChoreRequest = ChoreRequest(
    id = id,
    householdId = householdId,
    batchId = batchId,
    choreId = choreId,
    fromMemberId = fromMemberId,
    toMemberId = toMemberId,
    toUserId = toUserId,
    createdByUserId = createdByUserId,
    note = note,
    createdAt = createdAt,
    state = ChoreRequestState.parse(state),
    resolvedAt = resolvedAt,
)
