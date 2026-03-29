package cz.dcervenka.choretracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.navigation.RootDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AppViewModel @Inject constructor(
    authRepository: AuthRepository,
    householdRepository: HouseholdRepository,
) : ViewModel() {
    internal val rootDestination: StateFlow<RootDestination> = combine(
        authRepository.authState,
        householdRepository.observeCurrentHousehold(),
    ) { authState, household ->
        when {
            authState !is AuthState.Authenticated -> RootDestination.Auth
            household == null -> RootDestination.Onboarding
            else -> RootDestination.Main
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RootDestination.Auth,
    )
}
