package cz.dcervenka.choretracker.feature.settings.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import cz.dcervenka.choretracker.core.design.components.SettingsListItem
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiState

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onOpenHousehold: () -> Unit,
    onOpenMembers: () -> Unit,
    onOpenChores: () -> Unit,
    onOpenAccount: () -> Unit,
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
                    HorizontalDivider()
                    SettingsListItem(
                        title = stringResource(R.string.settings_manage_members_title),
                        subtitle = stringResource(R.string.settings_members_description, uiState.members.size),
                        onClick = onOpenMembers,
                    )
                    HorizontalDivider()
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
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(
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

@Composable
fun HouseholdSettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onHouseholdNameChange: (String) -> Unit,
    onSaveHouseholdName: () -> Unit,
    onRefreshInvite: () -> Unit,
) {
    val spacing = LocalSpacing.current

    ChoreScaffold(
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
                            onValueChange = onHouseholdNameChange,
                            label = { Text(text = stringResource(R.string.settings_household_name)) },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                autoCorrectEnabled = true,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        PrimaryButton(
                            text = stringResource(R.string.settings_save_household_name),
                            onClick = onSaveHouseholdName,
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
                            onClick = onRefreshInvite,
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

@Composable
fun MembersSettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onMemberInputChange: (String) -> Unit,
    onAddMember: () -> Unit,
) {
    val spacing = LocalSpacing.current

    ChoreScaffold(
        topBar = {
            ChoreTopAppBar(
                title = stringResource(R.string.settings_manage_members_title),
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
                    title = stringResource(R.string.household_members),
                    subtitle = stringResource(R.string.household_member_count, uiState.members.size),
                )
            }
            item {
                SectionCard(title = stringResource(R.string.household_members)) {
                    if (uiState.members.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.settings_members_empty_title),
                            message = stringResource(R.string.settings_members_empty_message),
                        )
                    } else {
                        uiState.members.forEach { member ->
                            Text(
                                text = stringResource(
                                    R.string.household_member_line,
                                    member.displayName,
                                    member.role.name.lowercase(),
                                ),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = uiState.memberInput,
                        onValueChange = onMemberInputChange,
                        label = { Text(text = stringResource(R.string.household_new_member)) },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            autoCorrectEnabled = true,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PrimaryButton(
                        text = stringResource(R.string.household_add_member),
                        onClick = onAddMember,
                    )
                }
            }
        }
    }
}

@Composable
fun ChoresSettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onChoreInputChange: (String) -> Unit,
    onAddChore: () -> Unit,
    onUpdateChoreActive: (String, Boolean) -> Unit,
) {
    val spacing = LocalSpacing.current

    ChoreScaffold(
        topBar = {
            ChoreTopAppBar(
                title = stringResource(R.string.settings_manage_chores_title),
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
                    title = stringResource(R.string.household_chores),
                    subtitle = stringResource(R.string.household_chore_count, uiState.chores.size),
                )
            }
            item {
                SectionCard(title = stringResource(R.string.household_chores)) {
                    if (uiState.chores.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.settings_chores_empty_title),
                            message = stringResource(R.string.settings_chores_empty_message),
                        )
                    } else {
                        uiState.chores.forEach { chore ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(text = chore.name)
                                Switch(
                                    checked = chore.isActive,
                                    onCheckedChange = { checked -> onUpdateChoreActive(chore.id, checked) },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = uiState.choreInput,
                        onValueChange = onChoreInputChange,
                        label = { Text(text = stringResource(R.string.household_new_chore)) },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            autoCorrectEnabled = true,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PrimaryButton(
                        text = stringResource(R.string.household_add_chore),
                        onClick = onAddChore,
                    )
                }
            }
        }
    }
}

@Composable
fun AccountSettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val profileSummary = uiState.userLabel ?: when {
        uiState.requiresConfiguration -> stringResource(R.string.settings_firebase_required)
        uiState.isSignedOut -> stringResource(R.string.settings_signed_out)
        else -> stringResource(R.string.settings_loading)
    }

    ChoreScaffold(
        topBar = {
            ChoreTopAppBar(
                title = stringResource(R.string.settings_account_title),
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
                    title = stringResource(R.string.settings_account_title),
                    subtitle = stringResource(R.string.settings_profile_description, profileSummary),
                )
            }
            item {
                SectionCard(title = stringResource(R.string.settings_account_actions)) {
                    Text(
                        text = stringResource(R.string.settings_dynamic_color_note),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    PrimaryButton(
                        text = stringResource(R.string.settings_sign_out),
                        onClick = onSignOut,
                    )
                }
            }
        }
    }
}

@Composable
private fun detailContentPadding(innerPadding: PaddingValues): PaddingValues {
    val spacing = LocalSpacing.current
    return PaddingValues(
        start = spacing.large,
        top = innerPadding.calculateTopPadding() + spacing.large,
        end = spacing.large,
        bottom = innerPadding.calculateBottomPadding() + spacing.large,
    )
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
        )
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
            onBack = {},
            onHouseholdNameChange = {},
            onSaveHouseholdName = {},
            onRefreshInvite = {},
        )
    }
}
