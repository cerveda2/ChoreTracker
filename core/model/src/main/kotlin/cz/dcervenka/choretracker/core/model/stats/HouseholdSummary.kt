package cz.dcervenka.choretracker.core.model.stats

data class HouseholdSummary(
    val totalCompletions: Int,
    val topContributor: MemberContribution?,
)
