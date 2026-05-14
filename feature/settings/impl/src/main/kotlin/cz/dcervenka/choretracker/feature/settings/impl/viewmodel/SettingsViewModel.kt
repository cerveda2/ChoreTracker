package cz.dcervenka.choretracker.feature.settings.impl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.domain.usecase.AddChoreUseCase
import cz.dcervenka.choretracker.core.domain.usecase.AddMemberUseCase
import cz.dcervenka.choretracker.core.domain.usecase.CreateInviteUseCase
import cz.dcervenka.choretracker.core.domain.usecase.DeleteChoreUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveAuthStateUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveChoresUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveCurrentHouseholdUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveInvitesUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveMembersUseCase
import cz.dcervenka.choretracker.core.domain.usecase.SignOutUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateChoreActiveUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateChoreCategoryUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateChoreFrequencyUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateChoreNameUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateCurrentMemberDisplayNameUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateDisplayNameUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateHouseholdNameUseCase
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiEvent
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiIntent
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeAuthStateUseCase: ObserveAuthStateUseCase,
    observeCurrentHouseholdUseCase: ObserveCurrentHouseholdUseCase,
    observeMembersUseCase: ObserveMembersUseCase,
    observeChoresUseCase: ObserveChoresUseCase,
    observeInvitesUseCase: ObserveInvitesUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val addMemberUseCase: AddMemberUseCase,
    private val addChoreUseCase: AddChoreUseCase,
    private val createInviteUseCase: CreateInviteUseCase,
    private val deleteChoreUseCase: DeleteChoreUseCase,
    private val updateDisplayNameUseCase: UpdateDisplayNameUseCase,
    private val updateCurrentMemberDisplayNameUseCase: UpdateCurrentMemberDisplayNameUseCase,
    private val updateChoreActiveUseCase: UpdateChoreActiveUseCase,
    private val updateChoreFrequencyUseCase: UpdateChoreFrequencyUseCase,
    private val updateChoreNameUseCase: UpdateChoreNameUseCase,
    private val updateChoreCategoryUseCase: UpdateChoreCategoryUseCase,
    private val updateHouseholdNameUseCase: UpdateHouseholdNameUseCase,
) : ViewModel() {
    private val _events = Channel<SettingsUiEvent>(Channel.BUFFERED)
    val events: Flow<SettingsUiEvent> = _events.receiveAsFlow()

    private var hydratedUserId: String? = null
    private var currentHouseholdId: String? = null
    private val accountDisplayNameInput = MutableStateFlow("")
    private val householdNameInput = MutableStateFlow("")
    private val memberInput = MutableStateFlow("")
    private val choreInput = MutableStateFlow("")
    private val choreCategoryInput = MutableStateFlow(ChoreCategory.OTHER)

    init {
        observeAuthStateUseCase()
            .onEach { state ->
                when (state) {
                    is AuthState.Authenticated -> {
                        if (hydratedUserId != state.user.id || accountDisplayNameInput.value.isBlank()) {
                            accountDisplayNameInput.value = state.user.displayName
                            hydratedUserId = state.user.id
                        }
                    }
                    else -> {
                        hydratedUserId = null
                        accountDisplayNameInput.value = ""
                    }
                }
            }
            .launchIn(viewModelScope)

        observeCurrentHouseholdUseCase()
            .onEach { household ->
                currentHouseholdId = household?.id
            }
            .launchIn(viewModelScope)
    }

    private val householdState = observeCurrentHouseholdUseCase()
        .flatMapLatest { household ->
            if (household == null) {
                combine(
                    accountDisplayNameInput,
                    householdNameInput,
                    memberInput,
                    choreInput,
                    choreCategoryInput,
                ) { currentAccountName, currentHouseholdName, currentMember, currentChore, currentCategory ->
                    SettingsUiState(
                        accountDisplayNameInput = currentAccountName,
                        householdNameInput = currentHouseholdName,
                        memberInput = currentMember,
                        choreInput = currentChore,
                        choreCategoryInput = currentCategory,
                    )
                }
            } else {
                if (householdNameInput.value.isBlank()) {
                    householdNameInput.value = household.name
                }
                combine(
                    observeMembersUseCase(household.id),
                    observeChoresUseCase(household.id),
                    observeInvitesUseCase(household.id),
                    combine(
                        accountDisplayNameInput,
                        householdNameInput,
                        memberInput,
                        choreInput,
                        choreCategoryInput,
                    ) { currentAccountName, currentHouseholdName, currentMember, currentChore, currentCategory ->
                        SettingsUiState(
                            accountDisplayNameInput = currentAccountName,
                            householdNameInput = currentHouseholdName,
                            memberInput = currentMember,
                            choreInput = currentChore,
                            choreCategoryInput = currentCategory,
                        )
                    },
                ) { members, chores, invites, draftState ->
                    draftState.copy(
                        household = household,
                        members = members,
                        chores = chores.filter { it.deletedAt == null },
                        invites = invites.sortedByDescending { it.createdAt },
                    )
                }
            }
        }

    val uiState: StateFlow<SettingsUiState> = combine(
        observeAuthStateUseCase(),
        householdState,
    ) { state, householdUiState ->
        when (state) {
            is AuthState.Authenticated -> householdUiState.copy(
                userLabel = state.user.displayName,
                userEmail = state.user.email,
                accountDisplayNameInput = accountDisplayNameInput.value,
            )
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
            is SettingsUiIntent.AccountDisplayNameChanged,
            is SettingsUiIntent.HouseholdNameChanged,
            is SettingsUiIntent.MemberInputChanged,
            is SettingsUiIntent.ChoreInputChanged,
            is SettingsUiIntent.ChoreCategoryInputChanged,
            -> handleInputIntent(intent)
            else -> handleActionIntent(intent)
        }
    }

    private fun handleInputIntent(intent: SettingsUiIntent) {
        when (intent) {
            is SettingsUiIntent.AccountDisplayNameChanged -> accountDisplayNameInput.value = intent.value
            is SettingsUiIntent.HouseholdNameChanged -> householdNameInput.value = intent.value
            is SettingsUiIntent.MemberInputChanged -> memberInput.value = intent.value
            is SettingsUiIntent.ChoreInputChanged -> choreInput.value = intent.value
            is SettingsUiIntent.ChoreCategoryInputChanged -> choreCategoryInput.value = intent.category
            else -> Unit
        }
    }

    private fun handleActionIntent(intent: SettingsUiIntent) {
        when (intent) {
            SettingsUiIntent.SaveAccountDisplayName -> saveAccountDisplayName()
            SettingsUiIntent.SignOut -> signOut()
            SettingsUiIntent.SaveHouseholdName -> saveHouseholdName()
            SettingsUiIntent.AddMember -> addMember()
            SettingsUiIntent.AddChore -> addChore()
            SettingsUiIntent.RefreshInvite -> refreshInvite()
            is SettingsUiIntent.UpdateChoreActive -> updateChoreActive(intent.choreId, intent.isActive)
            is SettingsUiIntent.DeleteChore -> deleteChore(intent.choreId)
            is SettingsUiIntent.UpdateChoreFrequency -> updateChoreFrequency(intent.choreId, intent.frequencyDays)
            is SettingsUiIntent.UpdateChoreName -> updateChoreName(intent.choreId, intent.name)
            is SettingsUiIntent.UpdateChoreCategory -> updateChoreCategory(intent.choreId, intent.category)
            else -> Unit
        }
    }

    private fun signOut() {
        viewModelScope.launch {
            signOutUseCase()
        }
    }

    private fun saveAccountDisplayName() {
        val sanitizedName = accountDisplayNameInput.value.trim()
        if (sanitizedName.isBlank()) return
        val householdId = currentHouseholdId
        viewModelScope.launch {
            val authUpdate = updateDisplayNameUseCase(sanitizedName)
            if (authUpdate is AppResult.Error) {
                _events.send(SettingsUiEvent.Error(authUpdate.message))
                return@launch
            }
            if (householdId != null) {
                val memberUpdate = updateCurrentMemberDisplayNameUseCase(householdId, sanitizedName)
                if (memberUpdate is AppResult.Error) {
                    _events.send(SettingsUiEvent.Error(memberUpdate.message))
                    return@launch
                }
            }
            _events.send(SettingsUiEvent.NameSaved)
        }
    }

    private fun saveHouseholdName() {
        val household = uiState.value.household ?: return
        viewModelScope.launch {
            val result = updateHouseholdNameUseCase(household.id, uiState.value.householdNameInput)
            if (result is AppResult.Success) {
                _events.send(SettingsUiEvent.NameSaved)
            } else if (result is AppResult.Error) {
                _events.send(SettingsUiEvent.Error(result.message))
            }
        }
    }

    private fun addMember() {
        val household = uiState.value.household ?: return
        viewModelScope.launch {
            val result = addMemberUseCase(household.id, uiState.value.memberInput)
            if (result is AppResult.Success) {
                memberInput.value = ""
                _events.send(SettingsUiEvent.MemberAdded)
            } else if (result is AppResult.Error) {
                _events.send(SettingsUiEvent.Error(result.message))
            }
        }
    }

    private fun addChore() {
        val household = uiState.value.household ?: return
        viewModelScope.launch {
            val result = addChoreUseCase(household.id, uiState.value.choreInput, choreCategoryInput.value)
            if (result is AppResult.Success) {
                choreInput.value = ""
                choreCategoryInput.value = ChoreCategory.OTHER
                _events.send(SettingsUiEvent.ChoreAdded)
            } else if (result is AppResult.Error) {
                _events.send(SettingsUiEvent.Error(result.message))
            }
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
            val result = deleteChoreUseCase(choreId)
            if (result is AppResult.Success) {
                _events.send(SettingsUiEvent.ChoreDeleted)
            } else if (result is AppResult.Error) {
                _events.send(SettingsUiEvent.Error(result.message))
            }
        }
    }

    private fun updateChoreFrequency(choreId: String, frequencyDays: Int?) {
        viewModelScope.launch {
            val result = updateChoreFrequencyUseCase(choreId, frequencyDays)
            if (result is AppResult.Success) {
                _events.send(SettingsUiEvent.ChoreSaved)
            } else if (result is AppResult.Error) {
                _events.send(SettingsUiEvent.Error(result.message))
            }
        }
    }

    private fun updateChoreName(choreId: String, name: String) {
        viewModelScope.launch {
            val result = updateChoreNameUseCase(choreId, name)
            if (result is AppResult.Success) {
                _events.send(SettingsUiEvent.ChoreSaved)
            } else if (result is AppResult.Error) {
                _events.send(SettingsUiEvent.Error(result.message))
            }
        }
    }

    private fun updateChoreCategory(choreId: String, category: ChoreCategory) {
        viewModelScope.launch {
            val result = updateChoreCategoryUseCase(choreId, category)
            if (result is AppResult.Success) {
                _events.send(SettingsUiEvent.ChoreSaved)
            } else if (result is AppResult.Error) {
                _events.send(SettingsUiEvent.Error(result.message))
            }
        }
    }
}
