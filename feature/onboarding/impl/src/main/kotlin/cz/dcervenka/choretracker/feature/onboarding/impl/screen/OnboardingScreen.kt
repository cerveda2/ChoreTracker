package cz.dcervenka.choretracker.feature.onboarding.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import cz.dcervenka.choretracker.core.design.ChoreTrackerTheme
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.PrimaryButton
import cz.dcervenka.choretracker.core.design.components.ScreenHeader
import cz.dcervenka.choretracker.core.design.components.SecondaryButton
import cz.dcervenka.choretracker.feature.onboarding.impl.contract.OnboardingUiState

@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    onHouseholdNameChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onInviteCodeChange: (String) -> Unit,
    onCreateHousehold: () -> Unit,
    onJoinHousehold: () -> Unit,
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
                title = stringResource(R.string.onboarding_title),
                subtitle = stringResource(R.string.onboarding_subtitle),
            )
            uiState.errorMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(
                value = uiState.displayName,
                onValueChange = onDisplayNameChange,
                label = { Text(stringResource(R.string.onboarding_your_name)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.householdName,
                onValueChange = onHouseholdNameChange,
                label = { Text(stringResource(R.string.onboarding_household_name)) },
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                text = stringResource(R.string.onboarding_create_household),
                onClick = onCreateHousehold,
                enabled = !uiState.isWorking,
            )
            OutlinedTextField(
                value = uiState.inviteCode,
                onValueChange = onInviteCodeChange,
                label = { Text(stringResource(R.string.onboarding_invite_code)) },
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                text = stringResource(R.string.onboarding_join_household),
                onClick = onJoinHousehold,
                enabled = !uiState.isWorking,
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
            onHouseholdNameChange = {},
            onDisplayNameChange = {},
            onInviteCodeChange = {},
            onCreateHousehold = {},
            onJoinHousehold = {},
        )
    }
}
