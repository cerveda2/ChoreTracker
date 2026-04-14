package cz.dcervenka.choretracker.feature.household.impl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.dcervenka.choretracker.core.domain.usecase.CreateInviteUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveChoresUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveCurrentHouseholdUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveInvitesUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveMembersUseCase
import cz.dcervenka.choretracker.feature.household.impl.contract.HouseholdUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HouseholdViewModel @Inject constructor(
    observeCurrentHouseholdUseCase: ObserveCurrentHouseholdUseCase,
    private val observeMembersUseCase: ObserveMembersUseCase,
    private val observeInvitesUseCase: ObserveInvitesUseCase,
    private val observeChoresUseCase: ObserveChoresUseCase,
    private val createInviteUseCase: CreateInviteUseCase,
) : ViewModel() {
    val uiState: StateFlow<HouseholdUiState> = observeCurrentHouseholdUseCase()
        .filterNotNull()
        .flatMapLatest { household ->
            combine(
                observeMembersUseCase(household.id),
                observeInvitesUseCase(household.id),
                observeChoresUseCase(household.id),
            ) { members, invites, chores ->
                HouseholdUiState(
                    household = household,
                    members = members,
                    chores = chores,
                    invites = invites,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HouseholdUiState(),
        )

    fun refreshInvite() {
        uiState.value.household?.let { household ->
            viewModelScope.launch { createInviteUseCase(household.id) }
        }
    }
}
