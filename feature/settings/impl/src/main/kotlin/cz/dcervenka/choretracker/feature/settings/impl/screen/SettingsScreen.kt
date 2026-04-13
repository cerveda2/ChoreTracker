package cz.dcervenka.choretracker.feature.settings.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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
    onDeleteChore: (String) -> Unit,
    onUpdateChoreActive: (String, Boolean) -> Unit,
    onUpdateChoreFrequency: (String, Int?) -> Unit,
) {
    val spacing = LocalSpacing.current
    var pendingDeleteChoreId by remember { mutableStateOf<String?>(null) }
    val pendingDeleteChore = uiState.chores.firstOrNull { it.id == pendingDeleteChoreId }
    var pendingFrequencyChoreId by remember { mutableStateOf<String?>(null) }
    val pendingFrequencyChore = uiState.chores.firstOrNull { it.id == pendingFrequencyChoreId }

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
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = chore.name)
                                    chore.frequencyDays?.let { days ->
                                        Text(
                                            text = stringResource(R.string.settings_chore_frequency_every_n_days, days),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { pendingFrequencyChoreId = chore.id }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Schedule,
                                            contentDescription = stringResource(
                                                R.string.settings_chore_set_frequency_title,
                                            ),
                                        )
                                    }
                                    Switch(
                                        checked = chore.isActive,
                                        onCheckedChange = { checked -> onUpdateChoreActive(chore.id, checked) },
                                    )
                                    IconButton(onClick = { pendingDeleteChoreId = chore.id }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = stringResource(R.string.household_delete_chore),
                                        )
                                    }
                                }
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

    if (pendingDeleteChore != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteChoreId = null },
            title = { Text(stringResource(R.string.household_delete_chore_title)) },
            text = { Text(stringResource(R.string.household_delete_chore_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteChore(pendingDeleteChore.id)
                        pendingDeleteChoreId = null
                    },
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteChoreId = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (pendingFrequencyChore != null) {
        var frequencyInput by remember(pendingFrequencyChore.id) {
            mutableStateOf(pendingFrequencyChore.frequencyDays?.toString() ?: "")
        }
        AlertDialog(
            onDismissRequest = { pendingFrequencyChoreId = null },
            title = { Text(stringResource(R.string.settings_chore_set_frequency_title)) },
            text = {
                OutlinedTextField(
                    value = frequencyInput,
                    onValueChange = { frequencyInput = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.settings_chore_set_frequency_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val days = frequencyInput.toIntOrNull()?.takeIf { it > 0 }
                        onUpdateChoreFrequency(pendingFrequencyChore.id, days)
                        pendingFrequencyChoreId = null
                    },
                ) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            onUpdateChoreFrequency(pendingFrequencyChore.id, null)
                            pendingFrequencyChoreId = null
                        },
                    ) {
                        Text(stringResource(R.string.settings_chore_clear_frequency))
                    }
                    TextButton(onClick = { pendingFrequencyChoreId = null }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            },
        )
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
                        text = stringResource(R.string.settings_theme_note),
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
