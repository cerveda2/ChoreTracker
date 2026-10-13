package cz.dcervenka.choretracker.feature.onboarding.impl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.domain.usecase.CreateHouseholdUseCase
import cz.dcervenka.choretracker.core.domain.usecase.JoinHouseholdUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveAuthStateUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveHouseholdRestoreStatusUseCase
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.model.error.AppError
import cz.dcervenka.choretracker.feature.onboarding.impl.contract.OnboardingUiIntent
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
    private val error = MutableStateFlow<AppError?>(null)

    private val authDisplayName = observeAuthStateUseCase().map { state ->
        val user = (state as? AuthState.Authenticated)?.user
        user?.displayName?.takeIf { it.isNotBlank() && it != user.email }
    }
    private val restoreStatus = observeHouseholdRestoreStatusUseCase()

    private val formState = combine(
        householdName,
        displayName,
        inviteCode,
        isWorking,
        error,
    ) { currentHousehold, currentDisplayName, currentInvite, working, error ->
        OnboardingUiState(
            householdName = currentHousehold,
            displayName = currentDisplayName,
            inviteCode = currentInvite,
            isWorking = working,
            error = error,
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
            restoreError = currentRestoreStatus.error,
            isRestoringRemoteHousehold = currentRestoreStatus.isRestoring,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OnboardingUiState(),
    )

    fun dispatch(intent: OnboardingUiIntent) {
        when (intent) {
            is OnboardingUiIntent.HouseholdNameChanged -> householdName.value = intent.value
            is OnboardingUiIntent.DisplayNameChanged -> displayName.value = intent.value
            is OnboardingUiIntent.InviteCodeChanged -> inviteCode.value = intent.value
            OnboardingUiIntent.CreateHousehold -> createHousehold()
            OnboardingUiIntent.JoinHousehold -> joinHousehold()
            OnboardingUiIntent.ClearError -> error.value = null
        }
    }

    private fun createHousehold() {
        submit {
            createHouseholdUseCase(
                name = householdName.value,
                ownerDisplayName = displayName.value,
            )
        }
    }

    private fun joinHousehold() {
        submit {
            joinHouseholdUseCase(
                code = inviteCode.value,
                currentUserDisplayName = uiState.value.displayName,
            )
        }
    }

    private fun submit(block: suspend () -> AppResult<*>) {
        viewModelScope.launch {
            isWorking.value = true
            error.value = null
            when (val result = block()) {
                is AppResult.Error -> error.value = result.error
                is AppResult.Success -> Unit
            }
            isWorking.value = false
        }
    }
}
