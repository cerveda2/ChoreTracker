package cz.dcervenka.choretracker.core.data.contract

import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.model.chore.ChoreRequest
import kotlinx.coroutines.flow.Flow

interface ChoreRequestRepository {
    fun observeRequests(householdId: String): Flow<List<ChoreRequest>>

    suspend fun sendRequests(householdId: String, toMemberId: String, choreIds: List<String>, note: String?): EmptyResult

    // Only the sender can cancel and only the recipient can decline. Either is a no-op once resolved.
    suspend fun cancelRequest(requestId: String): EmptyResult

    suspend fun declineRequest(requestId: String): EmptyResult
}
