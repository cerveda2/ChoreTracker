package cz.dcervenka.choretracker.feature.dashboard.impl.contract

import cz.dcervenka.choretracker.core.common.UiState
import cz.dcervenka.choretracker.core.model.chore.ChoreRequests
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.stats.DashboardSnapshot
import cz.dcervenka.choretracker.core.model.stats.RecentCompletion
import cz.dcervenka.choretracker.core.model.sync.SyncState

data class DashboardUiState(
    val snapshot: DashboardSnapshot? = null,
    val members: List<HouseholdMember> = emptyList(),
    val isManager: Boolean = false,
    val currentUserId: String? = null,
    val allCompletions: List<RecentCompletion> = emptyList(),
    val syncState: SyncState? = null,
    val isRefreshing: Boolean = false,
    val showNotificationsBanner: Boolean = false,
    // Null hides the indicator: nothing syncs in preview mode.
    val syncStatus: SyncStatus? = null,
    val requests: ChoreRequests = ChoreRequests(),
) : UiState
