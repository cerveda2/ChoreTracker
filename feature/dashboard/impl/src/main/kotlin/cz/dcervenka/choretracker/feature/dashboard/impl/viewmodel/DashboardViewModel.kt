package cz.dcervenka.choretracker.feature.dashboard.impl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.dcervenka.choretracker.core.domain.usecase.LogCompletionUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveCurrentDashboardUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveCurrentHouseholdUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveMembersUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveRecentCompletionsUseCase
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.DashboardUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DashboardViewModel @Inject constructor(
    observeCurrentDashboardUseCase: ObserveCurrentDashboardUseCase,
    observeCurrentHouseholdUseCase: ObserveCurrentHouseholdUseCase,
    observeMembersUseCase: ObserveMembersUseCase,
    observeRecentCompletionsUseCase: ObserveRecentCompletionsUseCase,
    private val logCompletionUseCase: LogCompletionUseCase,
) : ViewModel() {
    val uiState: StateFlow<DashboardUiState> = observeCurrentHouseholdUseCase()
        .filterNotNull()
        .flatMapLatest { household ->
            combine(
                observeCurrentDashboardUseCase(),
                observeMembersUseCase(household.id),
                observeRecentCompletionsUseCase(household.id, limit = Int.MAX_VALUE),
            ) { snapshot, members, completions ->
                DashboardUiState(
                    snapshot = snapshot,
                    members = members,
                    allCompletions = completions,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState(),
        )

    fun logCompletion(householdId: String, choreId: String, participantIds: List<String>, note: String?) {
        viewModelScope.launch {
            logCompletionUseCase(
                householdId = householdId,
                choreId = choreId,
                participantMemberIds = participantIds,
                note = note,
            )
        }
    }
}
