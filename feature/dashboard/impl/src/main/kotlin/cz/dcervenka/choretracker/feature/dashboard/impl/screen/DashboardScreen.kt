package cz.dcervenka.choretracker.feature.dashboard.impl.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import cz.dcervenka.choretracker.core.design.ChoreTrackerTheme
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.PreviewData
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.LoadingState
import cz.dcervenka.choretracker.core.design.components.PrimaryButton
import cz.dcervenka.choretracker.core.design.components.ScreenHeader
import cz.dcervenka.choretracker.core.design.components.SectionCard
import cz.dcervenka.choretracker.core.formatters.formatInstantForLocale
import cz.dcervenka.choretracker.core.formatters.formatLocalDateForLocale
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.stats.RecentCompletion
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.DashboardUiState

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onLogCompletion: (householdId: String, choreId: String, participantIds: List<String>, note: String?) -> Unit,
    onSeeAllCompletions: () -> Unit,
    onOpenCompletion: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    var selectedChoreId by remember { mutableStateOf<String?>(null) }
    var selectedNote by remember { mutableStateOf("") }
    val selectedMembers = remember { mutableStateListOf<String>() }
    val snapshot = uiState.snapshot

    if (snapshot == null) {
        LoadingState(message = stringResource(R.string.dashboard_loading))
    } else {
        val quickLogChores = snapshot.activeChores.sortedForQuickLog(uiState.allCompletions).take(8)
        val highlightedCompletions = uiState.allCompletions.take(3)
        val staleItems = snapshot.staleChores.filter { it.status != "OK" }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            item {
                Column(modifier = Modifier.padding(spacing.large)) {
                    ScreenHeader(
                        title = snapshot.household.name,
                        subtitle = stringResource(R.string.dashboard_recent_balance),
                    )
                }
            }
            item {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = spacing.large),
                    horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                ) {
                    items(snapshot.memberContributions, key = { it.memberId }) { contribution ->
                        Card {
                            Column(modifier = Modifier.padding(spacing.medium)) {
                                Text(contribution.displayName, style = MaterialTheme.typography.titleMedium)
                                Text("${contribution.totalCount}", style = MaterialTheme.typography.headlineSmall)
                                Text(
                                    stringResource(R.string.dashboard_last_30d, contribution.last30DaysCount),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
            item {
                Column(
                    modifier = Modifier.padding(horizontal = spacing.large),
                    verticalArrangement = Arrangement.spacedBy(spacing.xSmall),
                ) {
                    Text(stringResource(R.string.dashboard_quick_log), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.dashboard_record_prompt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = spacing.large),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                ) {
                    items(quickLogChores, key = { "quick-${it.id}" }) { chore ->
                        PrimaryButton(
                            text = chore.name,
                            onClick = {
                                selectedChoreId = chore.id
                                selectedMembers.clear()
                                selectedNote = ""
                            },
                            fillMaxWidth = false,
                        )
                    }
                }
            }
            item {
                SectionCard(
                    title = stringResource(R.string.dashboard_recent_completions),
                    modifier = Modifier.padding(horizontal = spacing.large),
                ) {
                    highlightedCompletions.forEach { completion ->
                        RecentCompletionRow(
                            completion = completion,
                            onClick = { onOpenCompletion(completion.completionId) },
                        )
                    }
                    if (uiState.allCompletions.size > highlightedCompletions.size) {
                        TextButton(onClick = onSeeAllCompletions) {
                            Text(stringResource(R.string.dashboard_see_all))
                        }
                    }
                }
            }
            item {
                SectionCard(
                    title = stringResource(R.string.dashboard_needs_attention),
                    modifier = Modifier.padding(horizontal = spacing.large),
                ) {
                    if (staleItems.isEmpty()) {
                        Text(
                            stringResource(R.string.dashboard_needs_attention_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        staleItems.forEach { stale ->
                            Column(verticalArrangement = Arrangement.spacedBy(spacing.xSmall)) {
                                Text(stale.choreName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    when {
                                        stale.lastCompletedDate == null ->
                                            stringResource(R.string.dashboard_stale_never_done)
                                        else -> stringResource(
                                            R.string.dashboard_stale_last_done,
                                            formatLocalDateForLocale(
                                                date = stale.lastCompletedDate!!,
                                                skeleton = "yMMMd",
                                            ),
                                            stale.daysSinceLastCompletion ?: 0,
                                        )
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedChoreId != null && snapshot != null) {
        AlertDialog(
            onDismissRequest = { selectedChoreId = null },
            title = { Text(stringResource(R.string.dashboard_log_completion)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                    Text(stringResource(R.string.dashboard_who_completed))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(spacing.small),
                        verticalArrangement = Arrangement.spacedBy(spacing.small),
                    ) {
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
                    }
                    OutlinedTextField(
                        value = selectedNote,
                        onValueChange = { selectedNote = it },
                        label = { Text(stringResource(R.string.dashboard_note)) },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            autoCorrectEnabled = true,
                        ),
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
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedChoreId = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
fun RecentCompletionsScreen(
    completions: List<RecentCompletion>,
    onBack: () -> Unit,
    onOpenCompletion: (String) -> Unit,
) {
    val spacing = LocalSpacing.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ScreenHeader(title = stringResource(R.string.dashboard_all_completions))
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.common_close))
                }
            }
        }
        items(completions, key = { it.completionId }) { completion ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenCompletion(completion.completionId) },
            ) {
                Column(modifier = Modifier.padding(spacing.medium)) {
                    RecentCompletionContent(completion = completion)
                }
            }
        }
    }
}

@Composable
fun RecentCompletionDetailScreen(
    completion: RecentCompletion?,
    onBack: () -> Unit,
) {
    val spacing = LocalSpacing.current

    if (completion == null) {
        LoadingState(message = stringResource(R.string.dashboard_completion_loading))
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ScreenHeader(title = completion.choreName)
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.common_close))
            }
        }
        SectionCard(title = stringResource(R.string.dashboard_completion_detail)) {
            RecentCompletionContent(
                completion = completion,
                dateSkeleton = "yMMMdHm",
            )
        }
    }
}

@Composable
private fun RecentCompletionRow(
    completion: RecentCompletion,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(LocalSpacing.current.medium)) {
            RecentCompletionContent(completion = completion)
        }
    }
}

@Composable
private fun RecentCompletionContent(
    completion: RecentCompletion,
    dateSkeleton: String = "yMMMd",
) {
    Text(completion.choreName, style = MaterialTheme.typography.titleMedium)
    Text(
        formatInstantForLocale(completion.completedAt, dateSkeleton),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(completion.participantNames.joinToString())
    completion.note?.takeIf(String::isNotBlank)?.let { Text(it) }
}

private fun List<Chore>.sortedForQuickLog(completions: List<RecentCompletion>): List<Chore> {
    val completionCounts = completions.groupingBy { it.choreName }.eachCount()
    return sortedWith(
        compareByDescending<Chore> { completionCounts[it.name] ?: 0 }
            .thenBy { it.name.lowercase() },
    )
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun DashboardScreenPreview() {
    ChoreTrackerTheme {
        DashboardScreen(
            uiState = DashboardUiState(
                snapshot = PreviewData.dashboardSnapshot,
                members = PreviewData.members,
                allCompletions = PreviewData.dashboardSnapshot.recentCompletions,
            ),
            onLogCompletion = { _, _, _, _ -> },
            onSeeAllCompletions = {},
            onOpenCompletion = {},
        )
    }
}
