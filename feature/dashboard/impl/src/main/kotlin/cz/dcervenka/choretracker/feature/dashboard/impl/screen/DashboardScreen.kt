package cz.dcervenka.choretracker.feature.dashboard.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import cz.dcervenka.choretracker.core.design.ChoreTrackerTheme
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.PreviewData
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.ChoreTopAppBar
import cz.dcervenka.choretracker.core.design.components.EmptyState
import cz.dcervenka.choretracker.core.design.components.LoadingState
import cz.dcervenka.choretracker.core.design.components.LogButton
import cz.dcervenka.choretracker.core.design.components.SectionCard
import cz.dcervenka.choretracker.core.design.toIcon
import cz.dcervenka.choretracker.core.formatters.formatLocalDateForLocale
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.stats.ChoreStatus
import cz.dcervenka.choretracker.core.model.stats.RecentCompletion
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.DashboardUiIntent
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.DashboardUiState
import cz.dcervenka.choretracker.feature.dashboard.impl.viewmodel.UndoEvent
import kotlinx.coroutines.flow.Flow

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onIntent: (DashboardUiIntent) -> Unit,
    undoEvents: Flow<UndoEvent>,
    onSeeAllCompletions: () -> Unit,
    onOpenCompletion: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.common_undo)
    val loggedMessage = stringResource(R.string.dashboard_logged_snackbar)

    LaunchedEffect(undoEvents) {
        undoEvents.collect { event ->
            val result = snackbarHostState.showSnackbar(
                message = loggedMessage.format(event.choreName),
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                onIntent(DashboardUiIntent.DeleteCompletion(event.completionId))
            }
        }
    }

    var selectedChoreId by remember { mutableStateOf<String?>(null) }
    var selectedNote by remember { mutableStateOf("") }
    val selectedMembers = remember { mutableStateListOf<String>() }
    val currentUserId = uiState.members.firstOrNull { it.isCurrentUser }?.id
    val openLogSheet: (String) -> Unit = { choreId ->
        selectedChoreId = choreId
        selectedMembers.clear()
        if (currentUserId != null) selectedMembers.add(currentUserId)
        selectedNote = ""
    }
    val snapshot = uiState.snapshot

    if (snapshot == null) {
        LoadingState(message = stringResource(R.string.dashboard_loading))
    } else {
        val quickLogChores = snapshot.activeChores.sortedForQuickLog(uiState.allCompletions).take(8)
        val stalenessByChoreId = snapshot.staleChores.associateBy { it.choreId }
        val highlightedCompletions = uiState.allCompletions.take(3)
        val staleItems = snapshot.staleChores.filter { it.status != ChoreStatus.OK }
        val categoryByChoreId = snapshot.activeChores.associate { it.id to it.category }

        ChoreScaffold(
            snackbarHostState = snackbarHostState,
            topBar = {
                ChoreTopAppBar(title = stringResource(R.string.dashboard_title))
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = spacing.large,
                    top = innerPadding.calculateTopPadding() + spacing.large,
                    end = spacing.large,
                    bottom = innerPadding.calculateBottomPadding() + spacing.large,
                ),
                verticalArrangement = Arrangement.spacedBy(spacing.medium),
            ) {
                uiState.syncState
                    ?.takeIf { it.pendingOperations > 0 || !it.lastErrorMessage.isNullOrBlank() }
                    ?.let { syncState ->
                        item {
                            RemoteSyncBanner(
                                syncState = syncState,
                                onRetrySync = { onIntent(DashboardUiIntent.RetrySync) },
                            )
                        }
                    }
                item {
                    SectionCard(title = snapshot.household.name) {
                        Text(
                            text = stringResource(R.string.dashboard_recent_balance),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                        ) {
                            items(snapshot.memberContributions, key = { it.memberId }) { contribution ->
                                Card {
                                    androidx.compose.foundation.layout.Column(
                                        modifier = Modifier.padding(spacing.medium),
                                    ) {
                                        Text(
                                            text = contribution.displayName,
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Text(
                                            text = "${contribution.totalCount}",
                                            style = MaterialTheme.typography.headlineSmall,
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.dashboard_share_percent,
                                                contribution.sharePercent,
                                            ),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.dashboard_last_30d,
                                                contribution.last30DaysCount,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.dashboard_quick_log)) {
                        if (quickLogChores.isEmpty()) {
                            EmptyState(
                                title = stringResource(R.string.dashboard_quick_log_empty_title),
                                message = stringResource(R.string.dashboard_quick_log_empty_message),
                            )
                        } else {
                            Text(
                                stringResource(R.string.dashboard_record_prompt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                            ) {
                                items(quickLogChores, key = { "quick-${it.id}" }) { chore ->
                                    val staleness = stalenessByChoreId[chore.id]
                                    val days = staleness?.daysSinceLastCompletion
                                    val subtitle = if (days != null) {
                                        stringResource(R.string.dashboard_days_ago, days)
                                    } else {
                                        stringResource(R.string.dashboard_never_done)
                                    }
                                    LogButton(
                                        text = chore.name,
                                        subtitle = subtitle,
                                        icon = chore.category.toIcon(),
                                        onClick = { openLogSheet(chore.id) },
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.dashboard_recent_completions)) {
                        if (highlightedCompletions.isEmpty()) {
                            EmptyState(
                                title = stringResource(R.string.dashboard_recent_completions_empty_title),
                                message = stringResource(R.string.dashboard_recent_completions_empty_message),
                            )
                        } else {
                            highlightedCompletions.forEachIndexed { index, completion ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = spacing.medium),
                                    )
                                }
                                RecentCompletionRow(
                                    completion = completion,
                                    onClick = { onOpenCompletion(completion.completionId) },
                                    roundedBackground = true,
                                )
                            }
                            if (uiState.allCompletions.size > highlightedCompletions.size) {
                                TextButton(onClick = onSeeAllCompletions) {
                                    Text(text = stringResource(R.string.dashboard_see_all))
                                }
                            }
                        }
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.dashboard_needs_attention)) {
                        if (staleItems.isEmpty()) {
                            Text(
                                text = stringResource(R.string.dashboard_needs_attention_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            staleItems.forEach { stale ->
                                val categoryIcon = categoryByChoreId[stale.choreId]?.toIcon()
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    androidx.compose.foundation.layout.Row(
                                        modifier = Modifier.padding(spacing.medium),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (categoryIcon != null) {
                                            Icon(
                                                imageVector = categoryIcon,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Spacer(modifier = Modifier.width(spacing.small))
                                        }
                                        androidx.compose.foundation.layout.Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(spacing.xSmall),
                                        ) {
                                            Text(
                                                text = stale.choreName,
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                            Text(
                                                text = when (val lastCompletedDate = stale.lastCompletedDate) {
                                                    null ->
                                                        stringResource(R.string.dashboard_stale_never_done)
                                                    else -> stringResource(
                                                        R.string.dashboard_stale_last_done,
                                                        formatLocalDateForLocale(
                                                            date = lastCompletedDate,
                                                            skeleton = "yMMMd",
                                                        ),
                                                        stale.daysSinceLastCompletion ?: 0,
                                                    )
                                                },
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        FilledTonalButton(
                                            onClick = { openLogSheet(stale.choreId) },
                                        ) {
                                            Text(text = stringResource(R.string.dashboard_log))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedChoreId != null && snapshot != null) {
        LogCompletionBottomSheet(
            uiState = uiState,
            selectedMembers = selectedMembers,
            selectedNote = selectedNote,
            onNoteChange = { selectedNote = it },
            onDismiss = { selectedChoreId = null },
            onConfirm = { completedAt ->
                onIntent(
                    DashboardUiIntent.LogCompletion(
                        householdId = snapshot.household.id,
                        choreId = selectedChoreId!!,
                        participantIds = selectedMembers.toList(),
                        note = selectedNote,
                        completedAt = completedAt,
                    ),
                )
                selectedChoreId = null
            },
        )
    }
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
            onIntent = {},
            undoEvents = kotlinx.coroutines.flow.emptyFlow(),
            onSeeAllCompletions = {},
            onOpenCompletion = {},
        )
    }
}
