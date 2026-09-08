package cz.dcervenka.choretracker.feature.stats.impl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.dcervenka.choretracker.core.domain.usecase.ObserveCurrentStatsUseCase
import cz.dcervenka.choretracker.core.model.stats.StatsPeriod
import cz.dcervenka.choretracker.feature.stats.impl.contract.StatsUiIntent
import cz.dcervenka.choretracker.feature.stats.impl.contract.StatsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    observeCurrentStatsUseCase: ObserveCurrentStatsUseCase,
) : ViewModel() {
    private val period = MutableStateFlow(StatsPeriod.MONTH)

    val uiState: StateFlow<StatsUiState> = period
        .flatMapLatest { selectedPeriod ->
            observeCurrentStatsUseCase(selectedPeriod).map { snapshot ->
                StatsUiState(snapshot = snapshot, period = selectedPeriod)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsUiState(period = period.value),
        )

    fun dispatch(intent: StatsUiIntent) {
        when (intent) {
            is StatsUiIntent.SelectPeriod -> period.value = intent.period
        }
    }
}
