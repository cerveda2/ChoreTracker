package cz.dcervenka.choretracker.feature.auth.impl.screen

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.PrimaryButton
import cz.dcervenka.choretracker.core.design.components.ScreenHeader
import cz.dcervenka.choretracker.core.design.components.SecondaryButton
import cz.dcervenka.choretracker.feature.auth.impl.contract.AuthUiIntent
import cz.dcervenka.choretracker.feature.auth.impl.contract.AuthUiState

@Composable
fun AuthScreen(
    uiState: AuthUiState,
    onIntent: (AuthUiIntent) -> Unit,
) {
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
                onValueChange = { onIntent(AuthUiIntent.DisplayNameChanged(it)) },
                label = { Text("Display name") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.email,
                onValueChange = { onIntent(AuthUiIntent.EmailChanged(it)) },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.password,
                onValueChange = { onIntent(AuthUiIntent.PasswordChanged(it)) },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                text = "Sign in",
                onClick = { onIntent(AuthUiIntent.SignInClicked) },
                enabled = !uiState.isWorking,
            )
            SecondaryButton(
                text = "Create account",
                onClick = { onIntent(AuthUiIntent.SignUpClicked) },
                enabled = !uiState.isWorking,
            )
            TextButton(
                onClick = { onIntent(AuthUiIntent.ContinuePreviewClicked) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continue in preview mode")
            }
        }
    }
}
