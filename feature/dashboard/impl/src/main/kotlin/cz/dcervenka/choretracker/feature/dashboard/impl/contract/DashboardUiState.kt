package cz.dcervenka.choretracker.feature.dashboard.impl.contract

import cz.dcervenka.choretracker.core.common.UiState
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.stats.DashboardSnapshot
import cz.dcervenka.choretracker.core.model.stats.RecentCompletion
import cz.dcervenka.choretracker.core.model.sync.SyncState

data class DashboardUiState(
    val snapshot: DashboardSnapshot? = null,
    val members: List<HouseholdMember> = emptyList(),
    val allCompletions: List<RecentCompletion> = emptyList(),
    val syncState: SyncState? = null,
    val errorMessage: String? = null,
) : UiState
