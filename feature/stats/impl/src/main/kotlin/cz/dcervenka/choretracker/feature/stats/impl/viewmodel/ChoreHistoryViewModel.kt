package cz.dcervenka.choretracker.feature.stats.impl.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.dcervenka.choretracker.core.domain.usecase.ObserveChoreCompletionsUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveCurrentHouseholdUseCase
import cz.dcervenka.choretracker.core.model.stats.RecentCompletion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ChoreHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeCurrentHouseholdUseCase: ObserveCurrentHouseholdUseCase,
    observeChoreCompletionsUseCase: ObserveChoreCompletionsUseCase,
) : ViewModel() {

    val choreName: String = checkNotNull(savedStateHandle["choreName"])

    private val choreId: String = checkNotNull(savedStateHandle["choreId"])

    val completions: StateFlow<List<RecentCompletion>> = observeCurrentHouseholdUseCase()
        .filterNotNull()
        .flatMapLatest { household ->
            observeChoreCompletionsUseCase(
                householdId = household.id,
                choreId = choreId,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
}
