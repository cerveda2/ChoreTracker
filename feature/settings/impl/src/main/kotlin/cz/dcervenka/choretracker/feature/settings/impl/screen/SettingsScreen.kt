package cz.dcervenka.choretracker.feature.settings.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
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
import cz.dcervenka.choretracker.core.design.components.PrimaryButton
import cz.dcervenka.choretracker.core.design.components.ScreenHeader
import cz.dcervenka.choretracker.core.design.components.SectionCard
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiState

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onHouseholdNameChange: (String) -> Unit,
    onSaveHouseholdName: () -> Unit,
    onMemberInputChange: (String) -> Unit,
    onChoreInputChange: (String) -> Unit,
    onAddMember: () -> Unit,
    onAddChore: () -> Unit,
    onRefreshInvite: () -> Unit,
    onUpdateChoreActive: (String, Boolean) -> Unit,
    onSignOut: () -> Unit,
) {
    val spacing = LocalSpacing.current

    ChoreScaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            item {
                ScreenHeader(title = stringResource(R.string.settings_title))
            }
            item {
                SectionCard(title = stringResource(R.string.settings_profile_title)) {
                    Text(
                        stringResource(
                            R.string.settings_current_profile,
                            uiState.userLabel ?: when {
                                uiState.requiresConfiguration -> stringResource(R.string.settings_firebase_required)
                                uiState.isSignedOut -> stringResource(R.string.settings_signed_out)
                                else -> stringResource(R.string.settings_loading)
                            },
                        ),
                    )
                }
            }
            uiState.household?.let { household ->
                item {
                    SectionCard(title = stringResource(R.string.settings_household_title)) {
                        OutlinedTextField(
                            value = uiState.householdNameInput,
                            onValueChange = onHouseholdNameChange,
                            label = { Text(stringResource(R.string.settings_household_name)) },
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
                        Text(
                            stringResource(R.string.settings_invite_code, household.inviteCode),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        PrimaryButton(
                            text = stringResource(R.string.household_refresh_invite),
                            onClick = onRefreshInvite,
                        )
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.household_members)) {
                        uiState.members.forEach { member ->
                            Text(
                                stringResource(
                                    R.string.household_member_line,
                                    member.displayName,
                                    member.role.name.lowercase(),
                                ),
                            )
                        }
                        OutlinedTextField(
                            value = uiState.memberInput,
                            onValueChange = onMemberInputChange,
                            label = { Text(stringResource(R.string.household_new_member)) },
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
                item {
                    SectionCard(title = stringResource(R.string.household_chores)) {
                        uiState.chores.forEach { chore ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(chore.name)
                                Switch(
                                    checked = chore.isActive,
                                    onCheckedChange = { checked -> onUpdateChoreActive(chore.id, checked) },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = uiState.choreInput,
                            onValueChange = onChoreInputChange,
                            label = { Text(stringResource(R.string.household_new_chore)) },
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
            item {
                SectionCard(title = stringResource(R.string.settings_account_actions)) {
                    Text(
                        stringResource(R.string.settings_dynamic_color_note),
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
                householdNameInput = PreviewData.household.name,
            ),
            onHouseholdNameChange = {},
            onSaveHouseholdName = {},
            onMemberInputChange = {},
            onChoreInputChange = {},
            onAddMember = {},
            onAddChore = {},
            onRefreshInvite = {},
            onUpdateChoreActive = { _, _ -> },
            onSignOut = {},
        )
    }
}
