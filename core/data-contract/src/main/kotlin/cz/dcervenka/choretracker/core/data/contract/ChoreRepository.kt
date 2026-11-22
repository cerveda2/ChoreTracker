package cz.dcervenka.choretracker.core.data.contract

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
import cz.dcervenka.choretracker.core.model.chore.ChoreSchedule
import kotlinx.coroutines.flow.Flow

interface ChoreRepository {
    fun observeChores(householdId: String): Flow<List<Chore>>

    fun observeCompletedChoreIds(householdId: String): Flow<Set<String>>

    suspend fun addChore(
        householdId: String,
        name: String,
        category: ChoreCategory,
        schedule: ChoreSchedule? = null,
        rotationMemberIds: List<String> = emptyList(),
        groupName: String? = null,
    ): AppResult<String>

    suspend fun deleteChore(choreId: String): EmptyResult

    suspend fun updateChoreActive(choreId: String, isActive: Boolean): EmptyResult

    suspend fun updateChoreName(choreId: String, name: String): EmptyResult

    suspend fun updateChoreSchedule(choreId: String, schedule: ChoreSchedule?): EmptyResult

    suspend fun updateChoreCategory(choreId: String, category: ChoreCategory): EmptyResult

    // A blank name takes the chore out of its group.
    suspend fun updateChoreGroup(choreId: String, groupName: String?): EmptyResult

    // Restarts whose-turn counting from now, so a member added later doesn't owe every past turn.
    suspend fun updateChoreRotation(choreId: String, memberIds: List<String>): EmptyResult
}
