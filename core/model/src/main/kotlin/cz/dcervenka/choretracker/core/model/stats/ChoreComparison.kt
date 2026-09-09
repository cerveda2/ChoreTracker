package cz.dcervenka.choretracker.core.model.stats

data class ChoreComparison(
    val choreId: String,
    val choreName: String,
    val countsByMemberId: Map<String, Int>,
    val leader: ChoreLeaderResult,
    val totalCount: Int,
    // The member with the lowest count, tie-broken toward whoever didn't do it last; null when
    // still tied after that (or with no members to choose from) - not every chore has an
    // obvious next owner.
    val nextTurnMemberId: String? = null,
)
