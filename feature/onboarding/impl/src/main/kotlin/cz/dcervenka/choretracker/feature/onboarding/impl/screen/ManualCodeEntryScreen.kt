package cz.dcervenka.choretracker.feature.onboarding.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.ChoreTopAppBar
import cz.dcervenka.choretracker.core.design.components.PrimaryButton
import cz.dcervenka.choretracker.core.design.components.SecondaryButton
import cz.dcervenka.choretracker.feature.onboarding.impl.contract.OnboardingUiIntent
import cz.dcervenka.choretracker.feature.onboarding.impl.contract.OnboardingUiState

@Composable
fun ManualCodeEntryScreen(
    uiState: OnboardingUiState,
    onIntent: (OnboardingUiIntent) -> Unit,
    onScanQr: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = LocalSpacing.current
    ChoreScaffold(
        topBar = {
            ChoreTopAppBar(
                title = stringResource(R.string.onboarding_join_household),
                onBackClick = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            uiState.errorMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(
                value = uiState.inviteCode,
                onValueChange = { onIntent(OnboardingUiIntent.InviteCodeChanged(it.uppercase())) },
                label = { Text(stringResource(R.string.onboarding_invite_code)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                text = stringResource(R.string.onboarding_join_household),
                onClick = { onIntent(OnboardingUiIntent.JoinHousehold) },
                enabled = !uiState.isWorking,
            )
            SecondaryButton(
                text = stringResource(R.string.onboarding_scan_qr_code),
                onClick = onScanQr,
            )
        }
    }
}
