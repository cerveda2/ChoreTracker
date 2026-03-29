package cz.dcervenka.choretracker.core.data.contract

import cz.dcervenka.choretracker.core.model.stats.DashboardSnapshot
import cz.dcervenka.choretracker.core.model.stats.StatsSnapshot
import kotlinx.coroutines.flow.Flow

interface StatsRepository {
    fun observeDashboard(householdId: String): Flow<DashboardSnapshot>

    fun observeStats(householdId: String): Flow<StatsSnapshot>
}
