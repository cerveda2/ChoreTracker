package cz.dcervenka.choretracker.core.model.stats

data class MonthlyBreakdown(
    val monthLabel: String,
    val countsByMember: Map<String, Int>,
    val totalCount: Int,
)
