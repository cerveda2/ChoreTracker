package cz.dcervenka.choretracker.feature.auth.impl.contract

import cz.dcervenka.choretracker.core.common.UiIntent
import cz.dcervenka.choretracker.core.common.UiState

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val authMode: AuthMode = AuthMode.SIGN_IN,
    val requiresConfiguration: Boolean = false,
    val isWorking: Boolean = false,
    val errorMessage: String? = null,
) : UiState

enum class AuthMode {
    SIGN_IN,
    SIGN_UP,
}

sealed interface AuthUiIntent : UiIntent {
    data class AuthModeChanged(val value: AuthMode) : AuthUiIntent
    data class DisplayNameChanged(val value: String) : AuthUiIntent
    data class EmailChanged(val value: String) : AuthUiIntent
    data class PasswordChanged(val value: String) : AuthUiIntent
    data object SignInClicked : AuthUiIntent
    data object SignUpClicked : AuthUiIntent
    data object ContinuePreviewClicked : AuthUiIntent
}
