package cz.dcervenka.choretracker.feature.auth.impl.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.domain.usecase.ContinueInPreviewModeUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveAuthStateUseCase
import cz.dcervenka.choretracker.core.domain.usecase.SignInUseCase
import cz.dcervenka.choretracker.core.domain.usecase.SignUpUseCase
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.feature.auth.impl.contract.AuthUiIntent
import cz.dcervenka.choretracker.feature.auth.impl.contract.AuthUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import android.app.Application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val application: Application,
    observeAuthStateUseCase: ObserveAuthStateUseCase,
    private val signInUseCase: SignInUseCase,
    private val signUpUseCase: SignUpUseCase,
    private val continueInPreviewModeUseCase: ContinueInPreviewModeUseCase,
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(AuthUiState())

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

    fun dispatch(intent: AuthUiIntent) {
        when (intent) {
            is AuthUiIntent.AuthModeChanged -> mutableUiState.update { current ->
                current.copy(authMode = intent.value, errorMessage = null, workingMessage = null)
            }
            is AuthUiIntent.DisplayNameChanged -> mutableUiState.update { current ->
                current.copy(displayName = intent.value, errorMessage = null, workingMessage = null)
            }
            is AuthUiIntent.EmailChanged -> mutableUiState.update { current ->
                current.copy(email = intent.value, errorMessage = null, workingMessage = null)
            }
            is AuthUiIntent.PasswordChanged -> mutableUiState.update { current ->
                current.copy(password = intent.value, errorMessage = null, workingMessage = null)
            }
            AuthUiIntent.SignInClicked -> submit { state ->
                validateSignIn(state)?.let {
                    return@submit PreparedSubmission(
                        workingMessage = application.getString(R.string.auth_signing_in),
                        execute = { it },
                    )
                }
                PreparedSubmission(
                    workingMessage = application.getString(R.string.auth_signing_in),
                    execute = { signInUseCase(state.email, state.password) },
                )
            }
            AuthUiIntent.SignUpClicked -> submit { state ->
                validateSignUp(state)?.let {
                    return@submit PreparedSubmission(
                        workingMessage = application.getString(R.string.auth_creating_account),
                        execute = { it },
                    )
                }
                PreparedSubmission(
                    workingMessage = application.getString(R.string.auth_creating_account),
                    execute = { signUpUseCase(state.email, state.password, state.displayName) },
                )
            }
            AuthUiIntent.ContinuePreviewClicked -> submit { state ->
                PreparedSubmission(
                    workingMessage = application.getString(R.string.auth_opening_preview),
                    execute = {
                        continueInPreviewModeUseCase(
                            state.displayName.ifBlank { "Preview User" },
                        )
                    },
                )
            }
        }
    }

    private fun submit(block: (AuthUiState) -> PreparedSubmission) {
        viewModelScope.launch {
            val currentState = mutableUiState.value
            val submission = block(currentState)
            mutableUiState.update { current ->
                current.copy(
                    isWorking = true,
                    errorMessage = null,
                    workingMessage = submission.workingMessage,
                )
            }
            when (val result = submission.execute()) {
                is AppResult.Error -> {
                    mutableUiState.update { current ->
                        current.copy(errorMessage = result.message, workingMessage = null)
                    }
                }
                is AppResult.Success -> Unit
            }
            mutableUiState.update { current ->
                current.copy(isWorking = false, workingMessage = null)
            }
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

    private data class PreparedSubmission(
        val workingMessage: String,
        val execute: suspend () -> EmptyResult,
    )
}
