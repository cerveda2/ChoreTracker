package cz.dcervenka.choretracker.feature.stats.impl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.dcervenka.choretracker.core.domain.usecase.ObserveCurrentStatsUseCase
import cz.dcervenka.choretracker.feature.stats.impl.contract.StatsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class StatsViewModel @Inject constructor(
    observeCurrentStatsUseCase: ObserveCurrentStatsUseCase,
) : ViewModel() {
    val uiState: StateFlow<StatsUiState> = observeCurrentStatsUseCase()
        .map(::StatsUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsUiState(),
        )
}
