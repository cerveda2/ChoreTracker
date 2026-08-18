package cz.dcervenka.choretracker.core.model.stats

data class ChoreComparison(
    val choreId: String,
    val choreName: String,
    val countsByMemberId: Map<String, Int>,
    val leader: ChoreLeaderResult,
    val totalCount: Int,
)
