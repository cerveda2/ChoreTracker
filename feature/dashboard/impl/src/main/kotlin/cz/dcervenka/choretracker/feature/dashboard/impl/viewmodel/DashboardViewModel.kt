package cz.dcervenka.choretracker.feature.dashboard.impl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.dcervenka.choretracker.core.data.contract.ChoreCompletionRepository
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.domain.usecase.ObserveCurrentDashboardUseCase
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
    householdRepository: HouseholdRepository,
    private val choreCompletionRepository: ChoreCompletionRepository,
) : ViewModel() {
    val uiState: StateFlow<DashboardUiState> = householdRepository.observeCurrentHousehold()
        .filterNotNull()
        .flatMapLatest { household ->
            combine(
                observeCurrentDashboardUseCase(),
                householdRepository.observeMembers(household.id),
            ) { snapshot, members ->
                DashboardUiState(snapshot = snapshot, members = members)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState(),
        )

    fun logCompletion(householdId: String, choreId: String, participantIds: List<String>, note: String?) {
        viewModelScope.launch {
            choreCompletionRepository.logCompletion(
                householdId = householdId,
                choreId = choreId,
                participantMemberIds = participantIds,
                note = note,
            )
        }
    }
}
