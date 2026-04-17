package cz.dcervenka.choretracker.feature.settings.impl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.dcervenka.choretracker.core.domain.usecase.AddChoreUseCase
import cz.dcervenka.choretracker.core.domain.usecase.AddMemberUseCase
import cz.dcervenka.choretracker.core.domain.usecase.CreateInviteUseCase
import cz.dcervenka.choretracker.core.domain.usecase.DeleteChoreUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveAuthStateUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveChoresUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveCurrentHouseholdUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveMembersUseCase
import cz.dcervenka.choretracker.core.domain.usecase.SignOutUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateChoreActiveUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateChoreFrequencyUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateChoreNameUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateHouseholdNameUseCase
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiIntent
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeAuthStateUseCase: ObserveAuthStateUseCase,
    observeCurrentHouseholdUseCase: ObserveCurrentHouseholdUseCase,
    observeMembersUseCase: ObserveMembersUseCase,
    observeChoresUseCase: ObserveChoresUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val addMemberUseCase: AddMemberUseCase,
    private val addChoreUseCase: AddChoreUseCase,
    private val createInviteUseCase: CreateInviteUseCase,
    private val deleteChoreUseCase: DeleteChoreUseCase,
    private val updateChoreActiveUseCase: UpdateChoreActiveUseCase,
    private val updateChoreFrequencyUseCase: UpdateChoreFrequencyUseCase,
    private val updateChoreNameUseCase: UpdateChoreNameUseCase,
    private val updateHouseholdNameUseCase: UpdateHouseholdNameUseCase,
) : ViewModel() {
    private val householdNameInput = MutableStateFlow("")
    private val memberInput = MutableStateFlow("")
    private val choreInput = MutableStateFlow("")

    private val householdState = observeCurrentHouseholdUseCase()
        .flatMapLatest { household ->
            if (household == null) {
                combine(
                    householdNameInput,
                    memberInput,
                    choreInput,
                ) { currentHouseholdName, currentMember, currentChore ->
                    SettingsUiState(
                        householdNameInput = currentHouseholdName,
                        memberInput = currentMember,
                        choreInput = currentChore,
                    )
                }
            } else {
                if (householdNameInput.value.isBlank()) {
                    householdNameInput.value = household.name
                }
                combine(
                    observeMembersUseCase(household.id),
                    observeChoresUseCase(household.id),
                    householdNameInput,
                    memberInput,
                    choreInput,
                ) { members, chores, currentHouseholdName, currentMember, currentChore ->
                    SettingsUiState(
                        household = household,
                        members = members,
                        chores = chores.filter { it.deletedAt == null },
                        householdNameInput = currentHouseholdName,
                        memberInput = currentMember,
                        choreInput = currentChore,
                    )
                }
            }
        }

    val uiState: StateFlow<SettingsUiState> = combine(
        observeAuthStateUseCase(),
        householdState,
    ) { state, householdUiState ->
        when (state) {
            is AuthState.Authenticated -> householdUiState.copy(userLabel = state.user.displayName)
            AuthState.RequiresConfiguration -> householdUiState.copy(requiresConfiguration = true)
            AuthState.SignedOut -> householdUiState.copy(isSignedOut = true)
            AuthState.Initializing -> householdUiState
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun dispatch(intent: SettingsUiIntent) {
        when (intent) {
            is SettingsUiIntent.HouseholdNameChanged -> householdNameInput.value = intent.value
            is SettingsUiIntent.MemberInputChanged -> memberInput.value = intent.value
            is SettingsUiIntent.ChoreInputChanged -> choreInput.value = intent.value
            SettingsUiIntent.SignOut -> signOut()
            SettingsUiIntent.SaveHouseholdName -> saveHouseholdName()
            SettingsUiIntent.AddMember -> addMember()
            SettingsUiIntent.AddChore -> addChore()
            SettingsUiIntent.RefreshInvite -> refreshInvite()
            is SettingsUiIntent.UpdateChoreActive -> updateChoreActive(intent.choreId, intent.isActive)
            is SettingsUiIntent.DeleteChore -> deleteChore(intent.choreId)
            is SettingsUiIntent.UpdateChoreFrequency -> updateChoreFrequency(intent.choreId, intent.frequencyDays)
            is SettingsUiIntent.UpdateChoreName -> updateChoreName(intent.choreId, intent.name)
        }
    }

    private fun signOut() {
        viewModelScope.launch {
            signOutUseCase()
        }
    }

    private fun saveHouseholdName() {
        val household = uiState.value.household ?: return
        viewModelScope.launch {
            updateHouseholdNameUseCase(household.id, uiState.value.householdNameInput)
        }
    }

    private fun addMember() {
        val household = uiState.value.household ?: return
        viewModelScope.launch {
            addMemberUseCase(household.id, uiState.value.memberInput)
            memberInput.value = ""
        }
    }

    private fun addChore() {
        val household = uiState.value.household ?: return
        viewModelScope.launch {
            addChoreUseCase(household.id, uiState.value.choreInput)
            choreInput.value = ""
        }
    }

    private fun refreshInvite() {
        val household = uiState.value.household ?: return
        viewModelScope.launch {
            createInviteUseCase(household.id)
        }
    }

    private fun updateChoreActive(choreId: String, isActive: Boolean) {
        viewModelScope.launch {
            updateChoreActiveUseCase(choreId, isActive)
        }
    }

    private fun deleteChore(choreId: String) {
        viewModelScope.launch {
            deleteChoreUseCase(choreId)
        }
    }

    private fun updateChoreFrequency(choreId: String, frequencyDays: Int?) {
        viewModelScope.launch {
            updateChoreFrequencyUseCase(choreId, frequencyDays)
        }
    }

    private fun updateChoreName(choreId: String, name: String) {
        viewModelScope.launch {
            updateChoreNameUseCase(choreId, name)
        }
    }
}
