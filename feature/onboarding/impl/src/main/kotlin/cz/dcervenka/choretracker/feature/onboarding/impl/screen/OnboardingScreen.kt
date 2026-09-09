package cz.dcervenka.choretracker.feature.onboarding.impl.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.dcervenka.choretracker.core.design.ChoreFonts
import cz.dcervenka.choretracker.core.design.ChoreTrackerTheme
import cz.dcervenka.choretracker.core.design.LocalMemberPalette
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.PrimaryButton
import cz.dcervenka.choretracker.core.design.components.SecondaryButton
import cz.dcervenka.choretracker.feature.onboarding.impl.contract.OnboardingUiIntent
import cz.dcervenka.choretracker.feature.onboarding.impl.contract.OnboardingUiState

@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    onIntent: (OnboardingUiIntent) -> Unit,
    onScanQr: () -> Unit,
) {
    val spacing = LocalSpacing.current
    var showCreateForm by rememberSaveable { mutableStateOf(false) }

    ChoreScaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Text(
                text = stringResource(R.string.auth_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.onboarding_headline),
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                text = stringResource(R.string.onboarding_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            OnboardingIllustration()
            uiState.errorMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(
                value = uiState.inviteCode,
                onValueChange = { onIntent(OnboardingUiIntent.InviteCodeChanged(it.uppercase())) },
                label = { Text(stringResource(R.string.onboarding_invite_code)) },
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = ChoreFonts.bricolageGrotesque(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                ),
                trailingIcon = {
                    IconButton(onClick = onScanQr) {
                        Icon(
                            imageVector = Icons.Outlined.QrCodeScanner,
                            contentDescription = stringResource(R.string.onboarding_scan),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.onboarding_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton(
                text = stringResource(R.string.onboarding_join_household),
                onClick = { onIntent(OnboardingUiIntent.JoinHousehold) },
                enabled = !uiState.isWorking && uiState.inviteCode.isNotBlank(),
            )
            SecondaryButton(
                text = stringResource(R.string.onboarding_create_new),
                onClick = { showCreateForm = !showCreateForm },
            )
            if (showCreateForm) {
                CreateHouseholdForm(uiState = uiState, onIntent = onIntent)
            }
            Text(
                text = stringResource(R.string.onboarding_footnote),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CreateHouseholdForm(
    uiState: OnboardingUiState,
    onIntent: (OnboardingUiIntent) -> Unit,
) {
    val spacing = LocalSpacing.current
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
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
            PrimaryButton(
                text = stringResource(R.string.onboarding_create_household),
                onClick = { onIntent(OnboardingUiIntent.CreateHousehold) },
                enabled = !uiState.isWorking,
            )
        }
    }
}

@Composable
private fun OnboardingIllustration(modifier: Modifier = Modifier) {
    val palette = LocalMemberPalette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 32.dp)
                .size(110.dp)
                .clip(CircleShape)
                .background(palette.color(0)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(110.dp)
                .clip(CircleShape)
                .background(palette.color(1).copy(alpha = 0.85f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 40.dp)
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 40.dp, bottom = 28.dp)
                .width(64.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.secondary),
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
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
            onScanQr = {},
        )
    }
}
