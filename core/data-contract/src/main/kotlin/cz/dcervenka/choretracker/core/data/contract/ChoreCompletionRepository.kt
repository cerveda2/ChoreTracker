package cz.dcervenka.choretracker.core.data.contract

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.model.stats.RecentCompletion
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface ChoreCompletionRepository {
    fun observeRecentCompletions(householdId: String, limit: Int = 10): Flow<List<RecentCompletion>>

    fun observeCompletionsByChore(householdId: String, choreId: String): Flow<List<RecentCompletion>>

    suspend fun logCompletion(
        householdId: String,
        choreId: String,
        participantMemberIds: List<String>,
        note: String?,
        completedAt: Instant? = null,
    ): AppResult<String>

    suspend fun updateCompletion(
        completionId: String,
        note: String?,
        participantMemberIds: List<String>,
    ): EmptyResult

    suspend fun deleteCompletion(completionId: String): EmptyResult
}
