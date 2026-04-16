package cz.dcervenka.choretracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.dcervenka.choretracker.core.domain.usecase.ObserveStartupDestinationUseCase
import cz.dcervenka.choretracker.core.model.app.StartupDestination
import cz.dcervenka.choretracker.navigation.RootDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    observeStartupDestinationUseCase: ObserveStartupDestinationUseCase,
) : ViewModel() {
    internal val rootDestination: StateFlow<RootDestination> = observeStartupDestinationUseCase()
        .map { destination ->
            when (destination) {
                StartupDestination.AUTH -> RootDestination.Auth
                StartupDestination.ONBOARDING -> RootDestination.Onboarding
                StartupDestination.MAIN -> RootDestination.Main
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RootDestination.Loading,
        )

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        viewModelScope.launch {
            rootDestination.first { it != RootDestination.Loading }
            _isReady.value = true
        }
    }
}
