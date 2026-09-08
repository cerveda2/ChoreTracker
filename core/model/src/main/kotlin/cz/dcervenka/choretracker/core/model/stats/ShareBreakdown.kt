package cz.dcervenka.choretracker.core.model.stats

// Replaces the old per-member sharePercent: a solo completion credits its one member, a shared
// one (2+ current-member participants) counts toward "Together" instead of splitting between
// them. percentByMemberId and togetherPercent are largest-remainder rounded so they always sum
// to exactly 100 (never 99 or 101 from naive per-value rounding).
data class ShareBreakdown(
    val soloCountByMemberId: Map<String, Int>,
    val sharedCount: Int,
    val totalCount: Int,
    val percentByMemberId: Map<String, Int>,
    val togetherPercent: Int,
)
