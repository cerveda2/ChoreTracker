package cz.dcervenka.choretracker.core.model.stats

import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.chore.ChoreCompletion
import cz.dcervenka.choretracker.core.model.household.Household
import cz.dcervenka.choretracker.core.model.household.HouseholdMember

data class HouseholdStatsInput(
    val household: Household,
    val members: List<HouseholdMember>,
    val chores: List<Chore>,
    val completions: List<ChoreCompletion>,
)
