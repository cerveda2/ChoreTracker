package cz.dcervenka.choretracker.feature.settings.impl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.domain.usecase.AddMemberUseCase
import cz.dcervenka.choretracker.core.domain.usecase.CreateInviteUseCase
import cz.dcervenka.choretracker.core.domain.usecase.CreateMemberInviteUseCase
import cz.dcervenka.choretracker.core.domain.usecase.DeleteMemberUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveAuthStateUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveCurrentHouseholdUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveInvitesUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveMembersUseCase
import cz.dcervenka.choretracker.core.domain.usecase.SignOutUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateCurrentMemberDisplayNameUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateDisplayNameUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateHouseholdNameUseCase
import cz.dcervenka.choretracker.core.model.auth.AuthState
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
    observeInvitesUseCase: ObserveInvitesUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val addMemberUseCase: AddMemberUseCase,
    private val createInviteUseCase: CreateInviteUseCase,
    private val createMemberInviteUseCase: CreateMemberInviteUseCase,
    private val deleteMemberUseCase: DeleteMemberUseCase,
    private val updateDisplayNameUseCase: UpdateDisplayNameUseCase,
    private val updateCurrentMemberDisplayNameUseCase: UpdateCurrentMemberDisplayNameUseCase,
    private val updateHouseholdNameUseCase: UpdateHouseholdNameUseCase,
) : ViewModel() {
    private val _events = Channel<SettingsUiEvent>(Channel.BUFFERED)
    val events: Flow<SettingsUiEvent> = _events.receiveAsFlow()

    private var hydratedUserId: String? = null
    private var currentHouseholdId: String? = null
    private val accountDisplayNameInput = MutableStateFlow("")
    private val householdNameInput = MutableStateFlow("")
    private val memberInput = MutableStateFlow("")

    private val currentHousehold = observeCurrentHouseholdUseCase()
        .onEach { household -> currentHouseholdId = household?.id }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        observeAuthStateUseCase()
            .onEach { state ->
                when (state) {
                    is AuthState.Authenticated -> {
                        if (hydratedUserId != state.user.id || accountDisplayNameInput.value.isBlank()) {
                            accountDisplayNameInput.value =
                                state.user.displayName.takeIf { it != state.user.email.orEmpty() }.orEmpty()
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
    }

    private val householdState = currentHousehold
        .flatMapLatest { household ->
            if (household == null) {
                combine(
                    accountDisplayNameInput,
                    householdNameInput,
                    memberInput,
                ) { currentAccountName, currentHouseholdName, currentMember ->
                    SettingsUiState(
                        accountDisplayNameInput = currentAccountName,
                        householdNameInput = currentHouseholdName,
                        memberInput = currentMember,
                    )
                }
            } else {
                if (householdNameInput.value.isBlank()) {
                    householdNameInput.value = household.name
                }
                combine(
                    observeMembersUseCase(household.id),
                    observeInvitesUseCase(household.id),
                    combine(
                        accountDisplayNameInput,
                        householdNameInput,
                        memberInput,
                    ) { currentAccountName, currentHouseholdName, currentMember ->
                        SettingsUiState(
                            accountDisplayNameInput = currentAccountName,
                            householdNameInput = currentHouseholdName,
                            memberInput = currentMember,
                        )
                    },
                ) { members, invites, draftState ->
                    draftState.copy(
                        household = household,
                        members = members,
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
            is AuthState.Authenticated -> {
                val memberName = householdUiState.members.find { it.isCurrentUser }
                    ?.displayName?.takeIf { it.isNotBlank() }
                val resolvedName = memberName
                    ?: state.user.displayName.takeIf { it != state.user.email.orEmpty() }
                    ?: state.user.email.orEmpty()
                householdUiState.copy(
                    userLabel = resolvedName,
                    userEmail = state.user.email,
                    accountDisplayNameInput = accountDisplayNameInput.value,
                )
            }
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
            -> handleInputIntent(intent)
            else -> handleActionIntent(intent)
        }
    }

    private fun handleInputIntent(intent: SettingsUiIntent) {
        when (intent) {
            is SettingsUiIntent.AccountDisplayNameChanged -> accountDisplayNameInput.value = intent.value
            is SettingsUiIntent.HouseholdNameChanged -> householdNameInput.value = intent.value
            is SettingsUiIntent.MemberInputChanged -> memberInput.value = intent.value
            else -> Unit
        }
    }

    private fun handleActionIntent(intent: SettingsUiIntent) {
        when (intent) {
            SettingsUiIntent.SaveAccountDisplayName -> saveAccountDisplayName()
            SettingsUiIntent.SignOut -> viewModelScope.launch { signOutUseCase() }
            SettingsUiIntent.SaveHouseholdName -> saveHouseholdName()
            SettingsUiIntent.AddMember -> addMember()
            SettingsUiIntent.RefreshInvite -> refreshInvite()
            is SettingsUiIntent.DeleteMember -> deleteMember(intent.memberId)
            is SettingsUiIntent.GenerateMemberInvite -> generateMemberInvite(intent.memberId)
            else -> Unit
        }
    }

    private fun generateMemberInvite(memberId: String) {
        val household = uiState.value.household ?: return
        viewModelScope.launch {
            val result = createMemberInviteUseCase(household.id, memberId)
            if (result is AppResult.Success) {
                _events.send(SettingsUiEvent.MemberInviteGenerated(result.value.code))
            } else if (result is AppResult.Error) {
                _events.send(SettingsUiEvent.Error(result.message))
            }
        }
    }

    private fun refreshInvite() {
        uiState.value.household?.let { h ->
            viewModelScope.launch { createInviteUseCase(h.id) }
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

    private fun deleteMember(memberId: String) {
        val household = uiState.value.household ?: return
        viewModelScope.launch {
            val result = deleteMemberUseCase(household.id, memberId)
            if (result is AppResult.Success) {
                _events.send(SettingsUiEvent.MemberDeleted)
            } else if (result is AppResult.Error) {
                _events.send(SettingsUiEvent.Error(result.message))
            }
        }
    }
}
