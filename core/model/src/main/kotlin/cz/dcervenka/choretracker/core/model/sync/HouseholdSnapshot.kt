package cz.dcervenka.choretracker.core.model.sync

import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.chore.ChoreCompletion
import cz.dcervenka.choretracker.core.model.household.Household
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.household.Invite

data class HouseholdSnapshot(
    val household: Household,
    val members: List<HouseholdMember>,
    val chores: List<Chore>,
    val completions: List<ChoreCompletion>,
    val invites: List<Invite>,
)
