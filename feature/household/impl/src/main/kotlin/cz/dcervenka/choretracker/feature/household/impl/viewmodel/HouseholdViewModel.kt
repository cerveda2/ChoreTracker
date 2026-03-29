package cz.dcervenka.choretracker.feature.household.impl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.dcervenka.choretracker.core.data.contract.ChoreRepository
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
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
    private val householdRepository: HouseholdRepository,
    private val choreRepository: ChoreRepository,
) : ViewModel() {
    private val memberInput = MutableStateFlow("")
    private val choreInput = MutableStateFlow("")

    val uiState: StateFlow<HouseholdUiState> = householdRepository.observeCurrentHousehold()
        .filterNotNull()
        .flatMapLatest { household ->
            combine(
                householdRepository.observeMembers(household.id),
                householdRepository.observeInvites(household.id),
                choreRepository.observeChores(household.id),
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
            householdRepository.addMember(household.id, state.memberInput)
            memberInput.value = ""
        }
    }

    fun addChore() {
        val state = uiState.value
        val household = state.household ?: return
        viewModelScope.launch {
            choreRepository.addChore(household.id, state.choreInput)
            choreInput.value = ""
        }
    }

    fun refreshInvite() {
        uiState.value.household?.let { household ->
            viewModelScope.launch { householdRepository.createInvite(household.id) }
        }
    }

    fun updateChoreActive(choreId: String, isActive: Boolean) {
        viewModelScope.launch {
            choreRepository.updateChoreActive(choreId, isActive)
        }
    }
}
