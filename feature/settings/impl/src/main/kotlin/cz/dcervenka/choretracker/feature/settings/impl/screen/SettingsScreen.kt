package cz.dcervenka.choretracker.feature.settings.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import cz.dcervenka.choretracker.core.design.ChoreTrackerTheme
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.PreviewData
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.ChoreTopAppBar
import cz.dcervenka.choretracker.core.design.components.SettingsListItem
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiState

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onOpenHousehold: () -> Unit,
    onOpenMembers: () -> Unit,
    onOpenChores: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenLanguage: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val profileSummary = uiState.userLabel ?: when {
        uiState.requiresConfiguration -> stringResource(R.string.settings_firebase_required)
        uiState.isSignedOut -> stringResource(R.string.settings_signed_out)
        else -> stringResource(R.string.settings_loading)
    }
    val householdSummary = uiState.household?.name ?: when {
        uiState.requiresConfiguration -> stringResource(R.string.settings_not_configured_summary)
        else -> stringResource(R.string.settings_no_household_summary)
    }

    ChoreScaffold(
        topBar = {
            ChoreTopAppBar(title = stringResource(R.string.settings_title))
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = spacing.large,
                top = innerPadding.calculateTopPadding() + spacing.medium,
                end = spacing.large,
                bottom = innerPadding.calculateBottomPadding() + spacing.large,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            item {
                SettingsGroup(title = stringResource(R.string.settings_profile_section)) {
                    SettingsListItem(
                        title = stringResource(R.string.settings_profile_title),
                        subtitle = stringResource(R.string.settings_profile_description, profileSummary),
                        onClick = onOpenAccount,
                    )
                }
            }
            item {
                SettingsGroup(title = stringResource(R.string.settings_management_section)) {
                    SettingsListItem(
                        title = stringResource(R.string.settings_manage_household_title),
                        subtitle = stringResource(R.string.settings_household_description, householdSummary),
                        onClick = onOpenHousehold,
                    )
                    androidx.compose.material3.HorizontalDivider()
                    SettingsListItem(
                        title = stringResource(R.string.settings_manage_members_title),
                        subtitle = stringResource(R.string.settings_members_description, uiState.members.size),
                        onClick = onOpenMembers,
                    )
                    androidx.compose.material3.HorizontalDivider()
                    SettingsListItem(
                        title = stringResource(R.string.settings_manage_chores_title),
                        subtitle = stringResource(R.string.settings_chores_description, uiState.chores.size),
                        onClick = onOpenChores,
                    )
                }
            }
            item {
                SettingsGroup(title = stringResource(R.string.settings_account_section)) {
                    SettingsListItem(
                        title = stringResource(R.string.settings_account_title),
                        subtitle = stringResource(R.string.settings_account_description),
                        onClick = onOpenAccount,
                    )
                    androidx.compose.material3.HorizontalDivider()
                    SettingsListItem(
                        title = stringResource(R.string.settings_language_title),
                        subtitle = stringResource(R.string.settings_language_description),
                        onClick = onOpenLanguage,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    val spacing = LocalSpacing.current

    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
        )
        Card {
            Column {
                content()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    ChoreTrackerTheme {
        SettingsScreen(
            uiState = SettingsUiState(
                userLabel = "Dana",
                household = PreviewData.household,
                members = PreviewData.members,
                chores = PreviewData.chores,
            ),
            onOpenHousehold = {},
            onOpenMembers = {},
            onOpenChores = {},
            onOpenAccount = {},
            onOpenLanguage = {},
        )
    }
}
