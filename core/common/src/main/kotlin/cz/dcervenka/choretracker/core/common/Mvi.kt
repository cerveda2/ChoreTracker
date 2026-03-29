package cz.dcervenka.choretracker.core.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

interface UiState

interface UiIntent

interface UiEffect

interface MviViewModel<S : UiState, I : UiIntent, E : UiEffect> {
    val uiState: StateFlow<S>
    val effects: Flow<E>

    fun dispatch(intent: I)
}

inline fun <S> MutableStateFlow<S>.updateState(transform: S.() -> S) {
    update { currentState -> currentState.transform() }
}
