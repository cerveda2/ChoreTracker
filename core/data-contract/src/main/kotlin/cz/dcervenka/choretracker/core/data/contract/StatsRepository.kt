package cz.dcervenka.choretracker.core.data.contract

import cz.dcervenka.choretracker.core.model.stats.HouseholdStatsInput
import kotlinx.coroutines.flow.Flow

interface StatsRepository {
    fun observeHouseholdStatsInput(householdId: String): Flow<HouseholdStatsInput>

    suspend fun getHouseholdStatsInput(householdId: String): HouseholdStatsInput
}
