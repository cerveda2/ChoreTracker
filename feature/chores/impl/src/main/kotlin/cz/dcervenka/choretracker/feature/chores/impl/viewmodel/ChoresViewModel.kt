package cz.dcervenka.choretracker.feature.chores.impl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.domain.usecase.AddChoreUseCase
import cz.dcervenka.choretracker.core.domain.usecase.DeleteChoreUseCase
import cz.dcervenka.choretracker.core.domain.usecase.DeleteCompletionUseCase
import cz.dcervenka.choretracker.core.domain.usecase.LogCompletionUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveChoresUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveCurrentHouseholdUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveMembersUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveStaleChoresUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateChoreActiveUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateChoreCategoryUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateChoreFrequencyUseCase
import cz.dcervenka.choretracker.core.domain.usecase.UpdateChoreNameUseCase
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import cz.dcervenka.choretracker.core.model.stats.ChoreStaleness
import cz.dcervenka.choretracker.feature.chores.impl.contract.ChoreFilter
import cz.dcervenka.choretracker.feature.chores.impl.contract.ChoresUiEvent
import cz.dcervenka.choretracker.feature.chores.impl.contract.ChoresUiIntent
import cz.dcervenka.choretracker.feature.chores.impl.contract.ChoresUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Suppress("TooManyFunctions")
@HiltViewModel
class ChoresViewModel @Inject constructor(
    observeCurrentHouseholdUseCase: ObserveCurrentHouseholdUseCase,
    observeChoresUseCase: ObserveChoresUseCase,
    observeMembersUseCase: ObserveMembersUseCase,
    observeStaleChoresUseCase: ObserveStaleChoresUseCase,
    private val addChoreUseCase: AddChoreUseCase,
    private val updateChoreNameUseCase: UpdateChoreNameUseCase,
    private val updateChoreCategoryUseCase: UpdateChoreCategoryUseCase,
    private val updateChoreFrequencyUseCase: UpdateChoreFrequencyUseCase,
    private val updateChoreActiveUseCase: UpdateChoreActiveUseCase,
    private val deleteChoreUseCase: DeleteChoreUseCase,
    private val logCompletionUseCase: LogCompletionUseCase,
    private val deleteCompletionUseCase: DeleteCompletionUseCase,
) : ViewModel() {

    private val _events = Channel<ChoresUiEvent>(Channel.BUFFERED)
    val events: Flow<ChoresUiEvent> = _events.receiveAsFlow()

    private val _undoChannel = Channel<UndoEvent>(Channel.BUFFERED)
    val undoEvents: Flow<UndoEvent> = _undoChannel.receiveAsFlow()

    private val filter = MutableStateFlow(ChoreFilter.ALL)
    private val query = MutableStateFlow("")

    // Chores/members/staleness only - filter and query are cheap, UI-only fields that shouldn't
    // force a re-fetch/re-filter of this data on every keystroke, so they're combined in
    // downstream of it instead of inside the same combine.
    val uiState: StateFlow<ChoresUiState> = observeCurrentHouseholdUseCase()
        .filterNotNull()
        .flatMapLatest { household ->
            combine(
                observeChoresUseCase(household.id),
                observeMembersUseCase(household.id),
                observeStaleChoresUseCase(),
            ) { chores, members, staleChores ->
                ChoresBaseData(
                    householdId = household.id,
                    chores = chores.filter { it.deletedAt == null },
                    staleness = staleChores.associateBy { it.choreId },
                    members = members,
                    isOwner = members.any { it.isCurrentUser && it.role == HouseholdRole.OWNER },
                )
            }
        }
        .combine(filter) { base, currentFilter -> base to currentFilter }
        .combine(query) { (base, currentFilter), currentQuery ->
            ChoresUiState(
                householdId = base.householdId,
                chores = base.chores,
                staleness = base.staleness,
                members = base.members,
                isOwner = base.isOwner,
                filter = currentFilter,
                query = currentQuery,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ChoresUiState(),
        )

    fun dispatch(intent: ChoresUiIntent) {
        when (intent) {
            is ChoresUiIntent.FilterChanged -> filter.value = intent.filter
            is ChoresUiIntent.QueryChanged -> query.value = intent.value
            is ChoresUiIntent.AddChore -> addChore(intent)
            is ChoresUiIntent.UpdateChore -> updateChore(intent)
            is ChoresUiIntent.DeleteChore -> deleteChore(intent.choreId)
            is ChoresUiIntent.LogCompletion -> logCompletion(intent)
            is ChoresUiIntent.DeleteCompletion -> deleteCompletion(intent.completionId)
        }
    }

    private fun addChore(intent: ChoresUiIntent.AddChore) {
        viewModelScope.launch {
            val result = addChoreUseCase(
                householdId = intent.householdId,
                name = intent.name,
                category = intent.category,
                frequencyDays = intent.frequencyDays,
            )
            if (result is AppResult.Success) {
                _events.send(ChoresUiEvent.ChoreAdded)
            } else if (result is AppResult.Error) {
                _events.send(ChoresUiEvent.Error(result.message))
            }
        }
    }

    // The four chore fields have independent use cases (each maps to its own Firestore/Room
    // write), so a single editor save fans out into up to four calls - only for the fields that
    // actually changed - and reports one failure if any of them fails.
    private fun updateChore(intent: ChoresUiIntent.UpdateChore) {
        val current = uiState.value.chores.find { it.id == intent.choreId } ?: return
        viewModelScope.launch {
            val results = buildList {
                if (current.name != intent.name) {
                    add(updateChoreNameUseCase(intent.choreId, intent.name))
                }
                if (current.category != intent.category) {
                    add(updateChoreCategoryUseCase(intent.choreId, intent.category))
                }
                if (current.frequencyDays != intent.frequencyDays) {
                    add(updateChoreFrequencyUseCase(intent.choreId, intent.frequencyDays))
                }
                if (current.isActive != intent.isActive) {
                    add(updateChoreActiveUseCase(intent.choreId, intent.isActive))
                }
            }
            val error = results.filterIsInstance<AppResult.Error>().firstOrNull()
            if (error != null) {
                _events.send(ChoresUiEvent.Error(error.message))
            } else {
                _events.send(ChoresUiEvent.ChoreSaved)
            }
        }
    }

    private fun deleteChore(choreId: String) {
        viewModelScope.launch {
            val result = deleteChoreUseCase(choreId)
            if (result is AppResult.Success) {
                _events.send(ChoresUiEvent.ChoreDeleted)
            } else if (result is AppResult.Error) {
                _events.send(ChoresUiEvent.Error(result.message))
            }
        }
    }

    private fun logCompletion(intent: ChoresUiIntent.LogCompletion) {
        viewModelScope.launch {
            val result = logCompletionUseCase(
                householdId = intent.householdId,
                choreId = intent.choreId,
                participantMemberIds = intent.participantIds,
                note = intent.note,
                completedAt = intent.completedAt,
            )
            when (result) {
                is AppResult.Success -> {
                    val choreName = uiState.value.chores.find { it.id == intent.choreId }?.name.orEmpty()
                    _undoChannel.send(UndoEvent(result.value, choreName))
                }
                is AppResult.Error -> _events.send(ChoresUiEvent.Error(result.message))
            }
        }
    }

    private fun deleteCompletion(completionId: String) {
        viewModelScope.launch {
            val result = deleteCompletionUseCase(completionId)
            if (result is AppResult.Error) {
                _events.send(ChoresUiEvent.Error(result.message))
            }
        }
    }

    private data class ChoresBaseData(
        val householdId: String,
        val chores: List<Chore>,
        val staleness: Map<String, ChoreStaleness>,
        val members: List<HouseholdMember>,
        val isOwner: Boolean,
    )
}
