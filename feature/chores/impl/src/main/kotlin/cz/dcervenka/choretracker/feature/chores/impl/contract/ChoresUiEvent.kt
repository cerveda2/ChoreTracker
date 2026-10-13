package cz.dcervenka.choretracker.feature.chores.impl.contract

import cz.dcervenka.choretracker.core.model.error.AppError

sealed interface ChoresUiEvent {
    data object ChoreAdded : ChoresUiEvent
    data object ChoreSaved : ChoresUiEvent
    data object ChoreDeleted : ChoresUiEvent
    data class Error(val error: AppError) : ChoresUiEvent
}
