package cz.dcervenka.choretracker.feature.auth.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.common.MviViewModel
import cz.dcervenka.choretracker.core.common.UiEffect
import cz.dcervenka.choretracker.core.common.UiIntent
import cz.dcervenka.choretracker.core.common.UiState
import cz.dcervenka.choretracker.core.common.updateState
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.PrimaryButton
import cz.dcervenka.choretracker.core.design.components.ScreenHeader
import cz.dcervenka.choretracker.core.design.components.SecondaryButton
import cz.dcervenka.choretracker.core.model.AuthState
import cz.dcervenka.choretracker.feature.auth.api.AUTH_ROUTE
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val requiresConfiguration: Boolean = false,
    val isWorking: Boolean = false,
    val errorMessage: String? = null,
) : UiState

sealed interface AuthUiIntent : UiIntent {
    data class DisplayNameChanged(val value: String) : AuthUiIntent
    data class EmailChanged(val value: String) : AuthUiIntent
    data class PasswordChanged(val value: String) : AuthUiIntent
    data object SignInClicked : AuthUiIntent
    data object SignUpClicked : AuthUiIntent
    data object ContinuePreviewClicked : AuthUiIntent
}

sealed interface AuthUiEffect : UiEffect {
    data class SubmissionFailed(val message: String) : AuthUiEffect
}

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

fun NavGraphBuilder.authScreen() {
    composable(route = AUTH_ROUTE) {
        val viewModel: AuthViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val spacing = LocalSpacing.current

        ChoreScaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(spacing.large),
                verticalArrangement = Arrangement.spacedBy(spacing.medium),
            ) {
                ScreenHeader(
                    title = "ChoreTracker",
                    subtitle = "Warm, offline-first home task tracking with stats at the center.",
                )
                if (uiState.requiresConfiguration) {
                    Card {
                        Text(
                            text = "Firebase is not configured yet. You can still explore the app in preview mode.",
                            modifier = Modifier.padding(spacing.medium),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                uiState.errorMessage?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(
                    value = uiState.displayName,
                    onValueChange = { viewModel.dispatch(AuthUiIntent.DisplayNameChanged(it)) },
                    label = { Text("Display name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uiState.email,
                    onValueChange = { viewModel.dispatch(AuthUiIntent.EmailChanged(it)) },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = { viewModel.dispatch(AuthUiIntent.PasswordChanged(it)) },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton(
                    text = "Sign in",
                    onClick = { viewModel.dispatch(AuthUiIntent.SignInClicked) },
                    enabled = !uiState.isWorking,
                )
                SecondaryButton(
                    text = "Create account",
                    onClick = { viewModel.dispatch(AuthUiIntent.SignUpClicked) },
                    enabled = !uiState.isWorking,
                )
                TextButton(
                    onClick = { viewModel.dispatch(AuthUiIntent.ContinuePreviewClicked) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Continue in preview mode")
                }
            }
        }
    }
}
