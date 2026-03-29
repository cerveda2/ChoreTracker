package cz.dcervenka.choretracker.feature.onboarding.impl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.feature.onboarding.impl.contract.OnboardingUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val householdRepository: HouseholdRepository,
) : ViewModel() {
    private val householdName = MutableStateFlow("")
    private val displayName = MutableStateFlow("")
    private val inviteCode = MutableStateFlow("")
    private val isWorking = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<OnboardingUiState> = combine(
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
            householdRepository.createHousehold(
                name = householdName.value,
                ownerDisplayName = displayName.value,
            )
        }
    }

    fun joinHousehold() {
        submit {
            householdRepository.joinHousehold(
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
