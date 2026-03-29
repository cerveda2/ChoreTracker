package cz.dcervenka.choretracker.feature.household.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cz.dcervenka.choretracker.core.design.LocalSpacing
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
                Text("Household", style = MaterialTheme.typography.headlineMedium)
                Text(uiState.household?.inviteCode?.let { "Active invite: $it" } ?: "No household yet")
                Button(onClick = onRefreshInvite) {
                    Text("Refresh invite")
                }
            }
        }
        item {
            Card(modifier = Modifier.padding(horizontal = spacing.large)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(spacing.small),
                ) {
                    Text("Members", style = MaterialTheme.typography.titleLarge)
                    uiState.members.forEach { member ->
                        Text("${member.displayName} • ${member.role.name.lowercase()}")
                    }
                    OutlinedTextField(
                        value = uiState.memberInput,
                        onValueChange = onMemberInputChange,
                        label = { Text("New member") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = onAddMember, modifier = Modifier.fillMaxWidth()) {
                        Text("Add member")
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.padding(horizontal = spacing.large)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(spacing.small),
                ) {
                    Text("Chores", style = MaterialTheme.typography.titleLarge)
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
                        label = { Text("New chore") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = onAddChore, modifier = Modifier.fillMaxWidth()) {
                        Text("Add chore")
                    }
                }
            }
        }
    }
}
