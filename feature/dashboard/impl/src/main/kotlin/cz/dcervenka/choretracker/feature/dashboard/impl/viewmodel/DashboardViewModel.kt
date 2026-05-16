package cz.dcervenka.choretracker.feature.dashboard.impl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.domain.usecase.DeleteCompletionUseCase
import cz.dcervenka.choretracker.core.domain.usecase.LogCompletionUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveCurrentDashboardUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveCurrentHouseholdUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveMembersUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveRecentCompletionsUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveSyncStateUseCase
import cz.dcervenka.choretracker.core.domain.usecase.RetryPendingSyncUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateCompletionUseCase
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.DashboardUiIntent
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.DashboardUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Instant

@HiltViewModel
class DashboardViewModel @Inject constructor(
    observeCurrentDashboardUseCase: ObserveCurrentDashboardUseCase,
    observeCurrentHouseholdUseCase: ObserveCurrentHouseholdUseCase,
    observeMembersUseCase: ObserveMembersUseCase,
    observeRecentCompletionsUseCase: ObserveRecentCompletionsUseCase,
    observeSyncStateUseCase: ObserveSyncStateUseCase,
    private val logCompletionUseCase: LogCompletionUseCase,
    private val updateCompletionUseCase: UpdateCompletionUseCase,
    private val deleteCompletionUseCase: DeleteCompletionUseCase,
    private val retryPendingSyncUseCase: RetryPendingSyncUseCase,
) : ViewModel() {

    private val _undoChannel = Channel<UndoEvent>(Channel.BUFFERED)
    val undoEvents: Flow<UndoEvent> = _undoChannel.receiveAsFlow()

    val uiState: StateFlow<DashboardUiState> = observeCurrentHouseholdUseCase()
        .filterNotNull()
        .flatMapLatest { household ->
            combine(
                observeCurrentDashboardUseCase(),
                observeMembersUseCase(household.id),
                observeRecentCompletionsUseCase(household.id, limit = 25),
                observeSyncStateUseCase(household.id),
            ) { snapshot, members, completions, syncState ->
                DashboardUiState(
                    snapshot = snapshot,
                    members = members,
                    allCompletions = completions,
                    syncState = syncState,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState(),
        )

    fun dispatch(intent: DashboardUiIntent) {
        when (intent) {
            is DashboardUiIntent.LogCompletion -> logCompletion(
                householdId = intent.householdId,
                choreId = intent.choreId,
                participantIds = intent.participantIds,
                note = intent.note,
                completedAt = intent.completedAt,
            )
            is DashboardUiIntent.UpdateCompletion -> updateCompletion(
                intent.completionId,
                intent.note,
                intent.participantIds,
            )
            is DashboardUiIntent.DeleteCompletion -> deleteCompletion(intent.completionId)
            DashboardUiIntent.RetrySync -> retrySync()
        }
    }

    private fun logCompletion(
        householdId: String,
        choreId: String,
        participantIds: List<String>,
        note: String?,
        completedAt: Instant? = null,
    ) {
        viewModelScope.launch {
            val result = logCompletionUseCase(
                householdId = householdId,
                choreId = choreId,
                participantMemberIds = participantIds,
                note = note,
                completedAt = completedAt,
            )
            if (result is AppResult.Success) {
                val choreName = uiState.value.snapshot?.activeChores
                    ?.find { it.id == choreId }?.name.orEmpty()
                _undoChannel.send(UndoEvent(result.value, choreName))
            }
        }
    }

    private fun updateCompletion(completionId: String, note: String?, participantIds: List<String>) {
        viewModelScope.launch {
            updateCompletionUseCase(completionId, note, participantIds)
        }
    }

    private fun deleteCompletion(completionId: String) {
        viewModelScope.launch {
            deleteCompletionUseCase(completionId)
        }
    }

    private fun retrySync() {
        viewModelScope.launch {
            val result = retryPendingSyncUseCase()
            if (result is AppResult.Error) {
                // The persistent sync banner already communicates the latest failure state.
            }
        }
    }
}
