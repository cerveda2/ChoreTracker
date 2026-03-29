package cz.dcervenka.choretracker.feature.auth.impl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.common.MviViewModel
import cz.dcervenka.choretracker.core.common.updateState
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.feature.auth.impl.contract.AuthUiEffect
import cz.dcervenka.choretracker.feature.auth.impl.contract.AuthUiIntent
import cz.dcervenka.choretracker.feature.auth.impl.contract.AuthUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel(),
    MviViewModel<AuthUiState, AuthUiIntent, AuthUiEffect> {

    private val mutableUiState = MutableStateFlow(AuthUiState())
    private val mutableEffects = MutableSharedFlow<AuthUiEffect>(extraBufferCapacity = 1)

    override val uiState: StateFlow<AuthUiState> = combine(
        authRepository.authState,
        mutableUiState,
    ) { authState, currentState ->
        currentState.copy(requiresConfiguration = authState is AuthState.RequiresConfiguration)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AuthUiState(),
    )

    override val effects: Flow<AuthUiEffect> = mutableEffects.asSharedFlow()

    override fun dispatch(intent: AuthUiIntent) {
        when (intent) {
            is AuthUiIntent.DisplayNameChanged -> mutableUiState.updateState {
                copy(displayName = intent.value, errorMessage = null)
            }
            is AuthUiIntent.EmailChanged -> mutableUiState.updateState {
                copy(email = intent.value, errorMessage = null)
            }
            is AuthUiIntent.PasswordChanged -> mutableUiState.updateState {
                copy(password = intent.value, errorMessage = null)
            }
            AuthUiIntent.SignInClicked -> submit { state ->
                authRepository.signIn(state.email, state.password)
            }
            AuthUiIntent.SignUpClicked -> submit { state ->
                authRepository.signUp(state.email, state.password, state.displayName)
            }
            AuthUiIntent.ContinuePreviewClicked -> submit { state ->
                authRepository.continueInPreviewMode(
                    state.displayName.ifBlank { "Preview User" },
                )
            }
        }
    }

    private fun submit(block: suspend (AuthUiState) -> EmptyResult) {
        viewModelScope.launch {
            val currentState = mutableUiState.value
            mutableUiState.updateState {
                copy(isWorking = true, errorMessage = null)
            }
            when (val result = block(currentState)) {
                is AppResult.Error -> {
                    mutableUiState.updateState {
                        copy(errorMessage = result.message)
                    }
                    mutableEffects.tryEmit(AuthUiEffect.SubmissionFailed(result.message))
                }
                is AppResult.Success -> Unit
            }
            mutableUiState.updateState { copy(isWorking = false) }
        }
    }
}
