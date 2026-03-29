package cz.dcervenka.choretracker.feature.dashboard.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.DashboardUiState

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onLogCompletion: (householdId: String, choreId: String, participantIds: List<String>, note: String?) -> Unit,
) {
    val spacing = LocalSpacing.current
    var selectedChoreId by remember { mutableStateOf<String?>(null) }
    var selectedNote by remember { mutableStateOf("") }
    val selectedMembers = remember { mutableStateListOf<String>() }
    val snapshot = uiState.snapshot

    if (snapshot == null) {
        Column(modifier = Modifier.fillMaxSize().padding(spacing.large)) {
            Text("Loading dashboard...")
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            item {
                Column(modifier = Modifier.padding(spacing.large)) {
                    Text(snapshot.household.name, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Recent balance and task flow",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.large),
                    horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                ) {
                    snapshot.memberContributions.forEach { contribution ->
                        Card(modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.padding(spacing.medium)) {
                                Text(contribution.displayName, style = MaterialTheme.typography.titleMedium)
                                Text("${contribution.totalCount}", style = MaterialTheme.typography.headlineMedium)
                                Text("last 30d: ${contribution.last30DaysCount}")
                            }
                        }
                    }
                }
            }
            item {
                Column(modifier = Modifier.padding(horizontal = spacing.large)) {
                    Text("Quick log", style = MaterialTheme.typography.titleLarge)
                }
            }
            items(snapshot.activeChores, key = { it.id }) { chore ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.large),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacing.medium),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(chore.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Tap to record who completed this.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = {
                            selectedChoreId = chore.id
                            selectedMembers.clear()
                            selectedNote = ""
                        }) {
                            Text("Log")
                        }
                    }
                }
            }
            item {
                Column(modifier = Modifier.padding(horizontal = spacing.large)) {
                    Text("Recent completions", style = MaterialTheme.typography.titleLarge)
                }
            }
            items(snapshot.recentCompletions, key = { it.completionId }) { completion ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.large),
                ) {
                    Column(modifier = Modifier.padding(spacing.medium)) {
                        Text(completion.choreName, style = MaterialTheme.typography.titleMedium)
                        Text(completion.participantNames.joinToString())
                        completion.note?.takeIf(String::isNotBlank)?.let { Text(it) }
                    }
                }
            }
            item {
                Column(modifier = Modifier.padding(horizontal = spacing.large)) {
                    Text("Needs attention", style = MaterialTheme.typography.titleLarge)
                }
            }
            items(snapshot.staleChores, key = { it.choreId }) { stale ->
                AssistChip(
                    onClick = {},
                    label = { Text("${stale.choreName}: ${stale.status}") },
                    modifier = Modifier.padding(horizontal = spacing.large),
                )
            }
        }
    }

    if (selectedChoreId != null && snapshot != null) {
        AlertDialog(
            onDismissRequest = { selectedChoreId = null },
            title = { Text("Log completion") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                    Text("Who completed this chore?")
                    uiState.members.forEach { member ->
                        FilterChip(
                            selected = selectedMembers.contains(member.id),
                            onClick = {
                                if (selectedMembers.contains(member.id)) {
                                    selectedMembers.remove(member.id)
                                } else {
                                    selectedMembers.add(member.id)
                                }
                            },
                            label = { Text(member.displayName) },
                        )
                    }
                    OutlinedTextField(
                        value = selectedNote,
                        onValueChange = { selectedNote = it },
                        label = { Text("Note") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedChoreId?.let { choreId ->
                            onLogCompletion(
                                snapshot.household.id,
                                choreId,
                                selectedMembers.toList(),
                                selectedNote,
                            )
                        }
                        selectedChoreId = null
                    },
                    enabled = selectedMembers.isNotEmpty(),
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedChoreId = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}
