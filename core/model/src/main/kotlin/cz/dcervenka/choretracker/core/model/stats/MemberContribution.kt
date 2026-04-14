package cz.dcervenka.choretracker.core.model.stats

data class MemberContribution(
    val memberId: String,
    val displayName: String,
    val totalCount: Int,
    val last30DaysCount: Int,
    val currentMonthCount: Int,
    val sharePercent: Int,
)
