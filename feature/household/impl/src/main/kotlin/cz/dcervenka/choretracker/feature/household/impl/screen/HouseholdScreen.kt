package cz.dcervenka.choretracker.feature.household.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
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
import cz.dcervenka.choretracker.core.design.components.PrimaryButton
import cz.dcervenka.choretracker.core.design.components.SectionCard
import cz.dcervenka.choretracker.feature.household.impl.contract.HouseholdUiState

@Composable
fun HouseholdScreen(
    uiState: HouseholdUiState,
    onMemberInputChange: (String) -> Unit,
    onChoreInputChange: (String) -> Unit,
    onAddMember: () -> Unit,
    onAddChore: () -> Unit,
    onRefreshInvite: () -> Unit,
    onUpdateChoreActive: (String, Boolean) -> Unit,
) {
    val spacing = LocalSpacing.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        item {
            Column(modifier = Modifier.padding(spacing.large)) {
                Text(stringResource(R.string.household_title), style = MaterialTheme.typography.headlineMedium)
                Text(
                    uiState.household?.inviteCode?.let {
                        stringResource(R.string.household_active_invite, it)
                    } ?: stringResource(R.string.household_no_household),
                )
                PrimaryButton(
                    text = stringResource(R.string.household_refresh_invite),
                    onClick = onRefreshInvite,
                )
            }
        }
        item {
            SectionCard(
                title = stringResource(R.string.household_members),
                modifier = Modifier.padding(horizontal = spacing.large),
            ) {
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
            SectionCard(
                title = stringResource(R.string.household_chores),
                modifier = Modifier.padding(horizontal = spacing.large),
            ) {
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
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun HouseholdScreenPreview() {
    ChoreTrackerTheme {
        HouseholdScreen(
            uiState = HouseholdUiState(
                household = PreviewData.household,
                members = PreviewData.members,
                chores = PreviewData.chores,
                invites = listOf(PreviewData.invite),
                memberInput = "Chris",
                choreInput = "Windows",
            ),
            onMemberInputChange = {},
            onChoreInputChange = {},
            onAddMember = {},
            onAddChore = {},
            onRefreshInvite = {},
            onUpdateChoreActive = { _, _ -> },
        )
    }
}
