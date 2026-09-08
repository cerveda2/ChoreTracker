package cz.dcervenka.choretracker.feature.chores.impl.contract

import cz.dcervenka.choretracker.core.common.UiState
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.stats.ChoreStaleness

data class ChoresUiState(
    val householdId: String? = null,
    val chores: List<Chore> = emptyList(),
    val staleness: Map<String, ChoreStaleness> = emptyMap(),
    val members: List<HouseholdMember> = emptyList(),
    val isOwner: Boolean = false,
    val filter: ChoreFilter = ChoreFilter.ALL,
    val query: String = "",
) : UiState
