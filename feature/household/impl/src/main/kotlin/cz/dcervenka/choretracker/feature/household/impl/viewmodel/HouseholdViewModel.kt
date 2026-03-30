package cz.dcervenka.choretracker.feature.household.impl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.dcervenka.choretracker.core.domain.usecase.AddChoreUseCase
import cz.dcervenka.choretracker.core.domain.usecase.AddMemberUseCase
import cz.dcervenka.choretracker.core.domain.usecase.CreateInviteUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveChoresUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveCurrentHouseholdUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveInvitesUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveMembersUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateChoreActiveUseCase
import cz.dcervenka.choretracker.feature.household.impl.contract.HouseholdUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HouseholdViewModel @Inject constructor(
    observeCurrentHouseholdUseCase: ObserveCurrentHouseholdUseCase,
    private val observeMembersUseCase: ObserveMembersUseCase,
    private val observeInvitesUseCase: ObserveInvitesUseCase,
    private val observeChoresUseCase: ObserveChoresUseCase,
    private val addMemberUseCase: AddMemberUseCase,
    private val addChoreUseCase: AddChoreUseCase,
    private val createInviteUseCase: CreateInviteUseCase,
    private val updateChoreActiveUseCase: UpdateChoreActiveUseCase,
) : ViewModel() {
    private val memberInput = MutableStateFlow("")
    private val choreInput = MutableStateFlow("")

    val uiState: StateFlow<HouseholdUiState> = observeCurrentHouseholdUseCase()
        .filterNotNull()
        .flatMapLatest { household ->
            combine(
                observeMembersUseCase(household.id),
                observeInvitesUseCase(household.id),
                observeChoresUseCase(household.id),
                memberInput,
                choreInput,
            ) { members, invites, chores, currentMember, currentChore ->
                HouseholdUiState(
                    household = household,
                    members = members,
                    chores = chores,
                    invites = invites,
                    memberInput = currentMember,
                    choreInput = currentChore,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HouseholdUiState(),
        )

    fun onMemberInputChange(value: String) {
        memberInput.value = value
    }

    fun onChoreInputChange(value: String) {
        choreInput.value = value
    }

    fun addMember() {
        val state = uiState.value
        val household = state.household ?: return
        viewModelScope.launch {
            addMemberUseCase(household.id, state.memberInput)
            memberInput.value = ""
        }
    }

    fun addChore() {
        val state = uiState.value
        val household = state.household ?: return
        viewModelScope.launch {
            addChoreUseCase(household.id, state.choreInput)
            choreInput.value = ""
        }
    }

    fun refreshInvite() {
        uiState.value.household?.let { household ->
            viewModelScope.launch { createInviteUseCase(household.id) }
        }
    }

    fun updateChoreActive(choreId: String, isActive: Boolean) {
        viewModelScope.launch {
            updateChoreActiveUseCase(choreId, isActive)
        }
    }
}
