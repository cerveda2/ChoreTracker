package cz.dcervenka.choretracker.core.model.stats

data class ChoreComparison(
    val choreId: String,
    val choreName: String,
    val countsByMember: Map<String, Int>,
    val leader: ChoreLeaderResult,
    val totalCount: Int,
)
