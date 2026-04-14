package cz.dcervenka.choretracker.core.model.stats

import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.household.Household

data class DashboardSnapshot(
    val household: Household,
    val summary: HouseholdSummary,
    val memberContributions: List<MemberContribution>,
    val activeChores: List<Chore>,
    val recentCompletions: List<RecentCompletion>,
    val staleChores: List<ChoreStaleness>,
)
