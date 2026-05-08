package cz.dcervenka.choretracker.feature.settings.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import cz.dcervenka.choretracker.core.design.ChoreTrackerTheme
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.PreviewData
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.ChoreTopAppBar
import cz.dcervenka.choretracker.core.design.components.EmptyState
import cz.dcervenka.choretracker.core.design.components.PrimaryButton
import cz.dcervenka.choretracker.core.design.components.ScreenHeader
import cz.dcervenka.choretracker.core.design.components.SectionCard
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiEvent
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiIntent
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun HouseholdSettingsScreen(
    uiState: SettingsUiState,
    events: Flow<SettingsUiEvent>,
    onBack: () -> Unit,
    onIntent: (SettingsUiIntent) -> Unit,
) {
    val spacing = LocalSpacing.current
    val snackbarHostState = remember { SnackbarHostState() }
    val msgNameSaved = stringResource(R.string.settings_feedback_name_saved)
    val msgError = stringResource(R.string.settings_feedback_error)
    LaunchedEffect(events) {
        events.collect { event ->
            val msg = when (event) {
                SettingsUiEvent.NameSaved -> msgNameSaved
                is SettingsUiEvent.Error -> event.message.ifBlank { msgError }
                else -> return@collect
            }
            snackbarHostState.showSnackbar(msg)
        }
    }

    ChoreScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            ChoreTopAppBar(
                title = stringResource(R.string.settings_manage_household_title),
                onBackClick = onBack,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = detailContentPadding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            item {
                ScreenHeader(
                    title = uiState.household?.name ?: stringResource(R.string.household_no_household),
                    subtitle = uiState.household?.let {
                        stringResource(R.string.settings_invite_code, it.inviteCode)
                    },
                )
            }
            if (uiState.household != null) {
                item {
                    SectionCard(title = stringResource(R.string.settings_household_title)) {
                        OutlinedTextField(
                            value = uiState.householdNameInput,
                            onValueChange = { onIntent(SettingsUiIntent.HouseholdNameChanged(it)) },
                            label = { Text(text = stringResource(R.string.settings_household_name)) },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                autoCorrectEnabled = true,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        PrimaryButton(
                            text = stringResource(R.string.settings_save_household_name),
                            onClick = { onIntent(SettingsUiIntent.SaveHouseholdName) },
                        )
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.household_invite_section)) {
                        Text(
                            text = stringResource(R.string.settings_invite_code, uiState.household.inviteCode),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        PrimaryButton(
                            text = stringResource(R.string.household_refresh_invite),
                            onClick = { onIntent(SettingsUiIntent.RefreshInvite) },
                        )
                    }
                }
            } else {
                item {
                    EmptyState(
                        title = stringResource(R.string.settings_household_empty_title),
                        message = stringResource(R.string.settings_household_empty_message),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HouseholdSettingsScreenPreview() {
    ChoreTrackerTheme {
        HouseholdSettingsScreen(
            uiState = SettingsUiState(
                household = PreviewData.household,
                householdNameInput = PreviewData.household.name,
            ),
            events = emptyFlow(),
            onBack = {},
            onIntent = {},
        )
    }
}
