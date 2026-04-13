package cz.dcervenka.choretracker.feature.onboarding.impl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.domain.usecase.CreateHouseholdUseCase
import cz.dcervenka.choretracker.core.domain.usecase.JoinHouseholdUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveAuthStateUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveHouseholdRestoreStatusUseCase
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.feature.onboarding.impl.contract.OnboardingUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    observeAuthStateUseCase: ObserveAuthStateUseCase,
    observeHouseholdRestoreStatusUseCase: ObserveHouseholdRestoreStatusUseCase,
    private val createHouseholdUseCase: CreateHouseholdUseCase,
    private val joinHouseholdUseCase: JoinHouseholdUseCase,
) : ViewModel() {
    private val householdName = MutableStateFlow("")
    private val displayName = MutableStateFlow("")
    private val inviteCode = MutableStateFlow("")
    private val isWorking = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    private val authDisplayName = observeAuthStateUseCase().map { state ->
        (state as? AuthState.Authenticated)?.user?.displayName
    }
    private val restoreStatus = observeHouseholdRestoreStatusUseCase()

    private val formState = combine(
        householdName,
        displayName,
        inviteCode,
        isWorking,
        errorMessage,
    ) { currentHousehold, currentDisplayName, currentInvite, working, error ->
        OnboardingUiState(
            householdName = currentHousehold,
            displayName = currentDisplayName,
            inviteCode = currentInvite,
            isWorking = working,
            errorMessage = error,
        )
    }

    val uiState: StateFlow<OnboardingUiState> = combine(
        authDisplayName,
        restoreStatus,
        formState,
    ) { currentAuthDisplayName, currentRestoreStatus, currentState ->
        currentState.copy(
            displayName = currentAuthDisplayName ?: currentState.displayName,
            canEditDisplayName = currentAuthDisplayName == null,
            restoreErrorMessage = currentRestoreStatus.errorMessage,
            isRestoringRemoteHousehold = currentRestoreStatus.isRestoring,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OnboardingUiState(),
    )

    fun onHouseholdNameChange(value: String) {
        householdName.value = value
    }

    fun onDisplayNameChange(value: String) {
        displayName.value = value
    }

    fun onInviteCodeChange(value: String) {
        inviteCode.value = value
    }

    fun createHousehold() {
        submit {
            createHouseholdUseCase(
                name = householdName.value,
                ownerDisplayName = displayName.value,
            )
        }
    }

    fun joinHousehold() {
        submit {
            joinHouseholdUseCase(
                code = inviteCode.value,
                currentUserDisplayName = displayName.value,
            )
        }
    }

    private fun submit(block: suspend () -> AppResult<*>) {
        viewModelScope.launch {
            isWorking.value = true
            errorMessage.value = null
            when (val result = block()) {
                is AppResult.Error -> errorMessage.value = result.message
                is AppResult.Success -> Unit
            }
            isWorking.value = false
        }
    }
}
