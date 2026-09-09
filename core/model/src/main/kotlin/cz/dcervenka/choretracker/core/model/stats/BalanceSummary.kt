package cz.dcervenka.choretracker.core.model.stats

// Derived from each member's last30DaysCount - null (see DashboardSnapshot.balance) when there
// are fewer than two members or nobody has logged anything in the window, since "who's behind"
// is meaningless in either case.
data class BalanceSummary(
    val leaderMemberId: String,
    val laggingMemberId: String,
    val gap: Int,
    val countsByMemberId: Map<String, Int>,
)
