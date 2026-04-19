package cz.dcervenka.choretracker.core.data.contract

import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
import kotlinx.coroutines.flow.Flow

interface ChoreRepository {
    fun observeChores(householdId: String): Flow<List<Chore>>

    suspend fun addChore(householdId: String, name: String, category: ChoreCategory): EmptyResult

    suspend fun deleteChore(choreId: String): EmptyResult

    suspend fun updateChoreActive(choreId: String, isActive: Boolean): EmptyResult

    suspend fun updateChoreName(choreId: String, name: String): EmptyResult

    suspend fun updateChoreFrequencyDays(choreId: String, frequencyDays: Int?): EmptyResult

    suspend fun updateChoreCategory(choreId: String, category: ChoreCategory): EmptyResult
}
