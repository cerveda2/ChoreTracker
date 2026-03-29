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
import cz.dcervenka.choretracker.core.design.LocalSpacing
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
                title = "Set up your household",
                subtitle = "Create a fresh household or join one with an invite code. The local Room database is already the source of truth.",
            )
            uiState.errorMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(
                value = uiState.displayName,
                onValueChange = onDisplayNameChange,
                label = { Text("Your name") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.householdName,
                onValueChange = onHouseholdNameChange,
                label = { Text("Household name") },
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                text = "Create household",
                onClick = onCreateHousehold,
                enabled = !uiState.isWorking,
            )
            OutlinedTextField(
                value = uiState.inviteCode,
                onValueChange = onInviteCodeChange,
                label = { Text("Invite code") },
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                text = "Join household",
                onClick = onJoinHousehold,
                enabled = !uiState.isWorking,
            )
        }
    }
}
