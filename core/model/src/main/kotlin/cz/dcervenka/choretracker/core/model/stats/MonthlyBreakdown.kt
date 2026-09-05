package cz.dcervenka.choretracker.core.model.stats

data class MonthlyBreakdown(
    val monthLabel: String,
    val countsByMemberId: Map<String, Int>,
    val totalCount: Int,
)
