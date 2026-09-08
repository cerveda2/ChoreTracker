package cz.dcervenka.choretracker.feature.chores.impl.contract

sealed interface ChoresUiEvent {
    data object ChoreAdded : ChoresUiEvent
    data object ChoreSaved : ChoresUiEvent
    data object ChoreDeleted : ChoresUiEvent
    data class Error(val message: String) : ChoresUiEvent
}
