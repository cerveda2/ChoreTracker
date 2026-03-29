package cz.dcervenka.choretracker.feature.household.impl.contract

import cz.dcervenka.choretracker.core.common.UiState
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.household.Household
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.household.Invite

data class HouseholdUiState(
    val household: Household? = null,
    val members: List<HouseholdMember> = emptyList(),
    val chores: List<Chore> = emptyList(),
    val invites: List<Invite> = emptyList(),
    val memberInput: String = "",
    val choreInput: String = "",
) : UiState
