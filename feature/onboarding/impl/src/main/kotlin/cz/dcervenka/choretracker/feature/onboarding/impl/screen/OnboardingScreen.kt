package cz.dcervenka.choretracker.feature.onboarding.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import cz.dcervenka.choretracker.core.design.ChoreTrackerTheme
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.PrimaryButton
import cz.dcervenka.choretracker.core.design.components.ScreenHeader
import cz.dcervenka.choretracker.core.design.components.SecondaryButton
import cz.dcervenka.choretracker.feature.onboarding.impl.contract.OnboardingUiIntent
import cz.dcervenka.choretracker.feature.onboarding.impl.contract.OnboardingUiState

@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    onIntent: (OnboardingUiIntent) -> Unit,
    onJoinHousehold: () -> Unit,
) {
    val spacing = LocalSpacing.current

    ChoreScaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            ScreenHeader(
                title = stringResource(R.string.onboarding_title),
                subtitle = stringResource(R.string.onboarding_subtitle),
            )
            if (uiState.isRestoringRemoteHousehold || uiState.restoreErrorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.restoreErrorMessage != null) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(spacing.xSmall),
                    ) {
                        Text(
                            text = stringResource(
                                if (uiState.restoreErrorMessage != null) {
                                    R.string.onboarding_restore_failed_title
                                } else {
                                    R.string.onboarding_restoring_title
                                },
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = if (uiState.restoreErrorMessage != null) {
                                stringResource(R.string.onboarding_restore_failed_message)
                            } else {
                                stringResource(R.string.onboarding_restoring_message)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            uiState.errorMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
            }
            if (uiState.canEditDisplayName) {
                OutlinedTextField(
                    value = uiState.displayName,
                    onValueChange = { onIntent(OnboardingUiIntent.DisplayNameChanged(it)) },
                    label = { Text(stringResource(R.string.onboarding_your_name)) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        autoCorrectEnabled = true,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = uiState.householdName,
                onValueChange = { onIntent(OnboardingUiIntent.HouseholdNameChanged(it)) },
                label = { Text(stringResource(R.string.onboarding_household_name)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    autoCorrectEnabled = true,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                text = stringResource(R.string.onboarding_create_household),
                onClick = { onIntent(OnboardingUiIntent.CreateHousehold) },
                enabled = !uiState.isWorking,
            )
            SecondaryButton(
                text = stringResource(R.string.onboarding_join_household),
                onClick = onJoinHousehold,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingScreenPreview() {
    ChoreTrackerTheme {
        OnboardingScreen(
            uiState = OnboardingUiState(
                householdName = "Sunny Flat",
                displayName = "Dana",
                inviteCode = "HOME42",
            ),
            onIntent = {},
            onJoinHousehold = {},
        )
    }
}
