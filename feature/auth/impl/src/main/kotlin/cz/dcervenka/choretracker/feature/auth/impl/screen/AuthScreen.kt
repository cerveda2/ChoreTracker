package cz.dcervenka.choretracker.feature.auth.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import cz.dcervenka.choretracker.core.design.ChoreTrackerTheme
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.PrimaryButton
import cz.dcervenka.choretracker.core.design.components.ScreenHeader
import cz.dcervenka.choretracker.feature.auth.impl.contract.AuthMode
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
                title = stringResource(R.string.auth_title),
                subtitle = stringResource(R.string.auth_subtitle),
            )
            if (uiState.requiresConfiguration) {
                Card {
                    Text(
                        text = stringResource(R.string.auth_firebase_not_configured),
                        modifier = Modifier.padding(spacing.medium),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            PrimaryTabRow(
                selectedTabIndex = uiState.authMode.ordinal,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                indicator = {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier
                            .tabIndicatorOffset(uiState.authMode.ordinal, matchContentSize = false)
                            .padding(horizontal = spacing.medium),
                        height = spacing.xSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                divider = {},
            ) {
                AuthMode.entries.forEach { mode ->
                    Tab(
                        selected = uiState.authMode == mode,
                        onClick = { onIntent(AuthUiIntent.AuthModeChanged(mode)) },
                        enabled = !uiState.isWorking,
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = {
                            Text(
                                text = if (mode == AuthMode.SIGN_IN) {
                                    stringResource(R.string.auth_sign_in)
                                } else {
                                    stringResource(R.string.auth_create_account)
                                },
                            )
                        },
                    )
                }
            }
            if (uiState.isWorking) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    uiState.workingMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (uiState.authMode == AuthMode.SIGN_UP) {
                OutlinedTextField(
                    value = uiState.displayName,
                    onValueChange = { onIntent(AuthUiIntent.DisplayNameChanged(it)) },
                    label = { Text(text = stringResource(R.string.auth_display_name)) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        autoCorrectEnabled = true,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = uiState.email,
                onValueChange = { onIntent(AuthUiIntent.EmailChanged(it)) },
                label = { Text(text = stringResource(R.string.auth_email)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.password,
                onValueChange = { onIntent(AuthUiIntent.PasswordChanged(it)) },
                label = { Text(text = stringResource(R.string.auth_password)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                text = if (uiState.authMode == AuthMode.SIGN_IN) {
                    stringResource(R.string.auth_sign_in)
                } else {
                    stringResource(R.string.auth_create_account)
                },
                onClick = {
                    onIntent(
                        if (uiState.authMode == AuthMode.SIGN_IN) {
                            AuthUiIntent.SignInClicked
                        } else {
                            AuthUiIntent.SignUpClicked
                        },
                    )
                },
                enabled = !uiState.isWorking,
                loading = uiState.isWorking,
            )
            TextButton(
                onClick = { onIntent(AuthUiIntent.ContinuePreviewClicked) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isWorking,
            ) {
                Text(text = stringResource(R.string.auth_continue_preview))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AuthScreenPreview() {
    ChoreTrackerTheme {
        AuthScreen(
            uiState = AuthUiState(
                authMode = AuthMode.SIGN_UP,
                displayName = "Dana",
                email = "dana@example.com",
                password = "password",
                requiresConfiguration = true,
            ),
            onIntent = {},
        )
    }
}
