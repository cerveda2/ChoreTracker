package cz.dcervenka.choretracker.core.model.stats

import cz.dcervenka.choretracker.core.model.chore.ChoreCategory

data class CategoryComparison(
    val category: ChoreCategory,
    val choreCount: Int,
    val countsByMember: Map<String, Int>,
    val totalCount: Int,
    val leader: ChoreLeaderResult,
)
