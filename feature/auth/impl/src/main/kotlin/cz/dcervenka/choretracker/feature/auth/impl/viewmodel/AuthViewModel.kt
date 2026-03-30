package cz.dcervenka.choretracker.feature.auth.impl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.domain.usecase.ContinueInPreviewModeUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveAuthStateUseCase
import cz.dcervenka.choretracker.core.domain.usecase.SignInUseCase
import cz.dcervenka.choretracker.core.domain.usecase.SignUpUseCase
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.feature.auth.impl.contract.AuthMode
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    observeAuthStateUseCase: ObserveAuthStateUseCase,
    private val signInUseCase: SignInUseCase,
    private val signUpUseCase: SignUpUseCase,
    private val continueInPreviewModeUseCase: ContinueInPreviewModeUseCase,
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(AuthUiState())
    private val mutableEffects = MutableSharedFlow<AuthUiEffect>(extraBufferCapacity = 1)

    val uiState: StateFlow<AuthUiState> = combine(
        observeAuthStateUseCase(),
        mutableUiState,
    ) { authState, currentState ->
        currentState.copy(requiresConfiguration = authState is AuthState.RequiresConfiguration)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AuthUiState(),
    )

    val effects: Flow<AuthUiEffect> = mutableEffects.asSharedFlow()

    fun dispatch(intent: AuthUiIntent) {
        when (intent) {
            is AuthUiIntent.AuthModeChanged -> mutableUiState.update { current ->
                current.copy(authMode = intent.value, errorMessage = null)
            }
            is AuthUiIntent.DisplayNameChanged -> mutableUiState.update { current ->
                current.copy(displayName = intent.value, errorMessage = null)
            }
            is AuthUiIntent.EmailChanged -> mutableUiState.update { current ->
                current.copy(email = intent.value, errorMessage = null)
            }
            is AuthUiIntent.PasswordChanged -> mutableUiState.update { current ->
                current.copy(password = intent.value, errorMessage = null)
            }
            AuthUiIntent.SignInClicked -> submit { state ->
                validateSignIn(state)?.let { return@submit it }
                signInUseCase(state.email, state.password)
            }
            AuthUiIntent.SignUpClicked -> submit { state ->
                validateSignUp(state)?.let { return@submit it }
                signUpUseCase(state.email, state.password, state.displayName)
            }
            AuthUiIntent.ContinuePreviewClicked -> submit { state ->
                continueInPreviewModeUseCase(
                    state.displayName.ifBlank { "Preview User" },
                )
            }
        }
    }

    private fun submit(block: suspend (AuthUiState) -> EmptyResult) {
        viewModelScope.launch {
            val currentState = mutableUiState.value
            mutableUiState.update { current ->
                current.copy(isWorking = true, errorMessage = null)
            }
            when (val result = block(currentState)) {
                is AppResult.Error -> {
                    mutableUiState.update { current ->
                        current.copy(errorMessage = result.message)
                    }
                    mutableEffects.tryEmit(AuthUiEffect.SubmissionFailed(result.message))
                }
                is AppResult.Success -> Unit
            }
            mutableUiState.update { current -> current.copy(isWorking = false) }
        }
    }

    private fun validateSignIn(state: AuthUiState): EmptyResult? = when {
        state.email.isBlank() -> AppResult.Error("Email is required.")
        state.password.isBlank() -> AppResult.Error("Password is required.")
        else -> null
    }

    private fun validateSignUp(state: AuthUiState): EmptyResult? = when {
        state.displayName.isBlank() -> AppResult.Error("Display name is required.")
        state.email.isBlank() -> AppResult.Error("Email is required.")
        state.password.isBlank() -> AppResult.Error("Password is required.")
        else -> null
    }
}
