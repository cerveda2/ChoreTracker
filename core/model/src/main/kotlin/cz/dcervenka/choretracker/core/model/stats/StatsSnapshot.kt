package cz.dcervenka.choretracker.core.model.stats

import cz.dcervenka.choretracker.core.model.household.Household

data class StatsSnapshot(
    val household: Household,
    val comparisons: List<ChoreComparison>,
    val monthlyBreakdown: List<MonthlyBreakdown>,
    val staleChores: List<ChoreStaleness>,
)
