package cz.dcervenka.choretracker.feature.dashboard.impl.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import cz.dcervenka.choretracker.core.design.ChoreTrackerTheme
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.PreviewData
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.ChoreTopAppBar
import cz.dcervenka.choretracker.core.design.components.EmptyState
import cz.dcervenka.choretracker.core.design.components.LoadingState
import cz.dcervenka.choretracker.core.design.components.PrimaryButton
import cz.dcervenka.choretracker.core.design.components.SectionCard
import cz.dcervenka.choretracker.core.formatters.formatInstantForLocale
import cz.dcervenka.choretracker.core.formatters.formatLocalDateForLocale
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.stats.ChoreStatus
import cz.dcervenka.choretracker.core.model.stats.RecentCompletion
import cz.dcervenka.choretracker.core.model.sync.SyncState
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.DashboardUiState

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onLogCompletion: (
        householdId: String,
        choreId: String,
        participantIds: List<String>,
        note: String?,
        completedAt: kotlin.time.Instant?,
    ) -> Unit,
    onRetrySync: () -> Unit,
    onSeeAllCompletions: () -> Unit,
    onOpenCompletion: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    var selectedChoreId by remember { mutableStateOf<String?>(null) }
    var selectedNote by remember { mutableStateOf("") }
    val selectedMembers = remember { mutableStateListOf<String>() }
    val currentUserId = uiState.members.firstOrNull { it.isCurrentUser }?.id
    val openLogSheet: (choreId: String) -> Unit = { choreId ->
        selectedChoreId = choreId
        selectedMembers.clear()
        if (currentUserId != null) selectedMembers.add(currentUserId)
        selectedNote = ""
    }
    val snapshot = uiState.snapshot
    val quickLog: (choreId: String) -> Unit = quickLog@{ choreId ->
        val householdId = snapshot?.household?.id ?: return@quickLog
        val userId = currentUserId ?: return@quickLog
        onLogCompletion(householdId, choreId, listOf(userId), null, null)
    }

    if (snapshot == null) {
        LoadingState(message = stringResource(R.string.dashboard_loading))
    } else {
        val quickLogChores = snapshot.activeChores.sortedForQuickLog(uiState.allCompletions).take(8)
        val stalenessByChoreId = snapshot.staleChores.associateBy { it.choreId }
        val highlightedCompletions = uiState.allCompletions.take(3)
        val staleItems = snapshot.staleChores.filter { it.status != ChoreStatus.OK }

        ChoreScaffold(
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
                                onRetrySync = onRetrySync,
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
                                    Column(
                                        modifier = Modifier.padding(spacing.medium),
                                    ) {
                                        Text(
                                            text = contribution.displayName,
                                            style = MaterialTheme.typography.titleMedium
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
                                    PrimaryButton(
                                        text = chore.name,
                                        subtitle = subtitle,
                                        onClick = { quickLog(chore.id) },
                                        onLongClick = { openLogSheet(chore.id) },
                                        fillMaxWidth = false,
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
                            highlightedCompletions.forEach { completion ->
                                RecentCompletionRow(
                                    completion = completion,
                                    onClick = { onOpenCompletion(completion.completionId) },
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
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { openLogSheet(stale.choreId) },
                                ) {
                                    Column(
                                        modifier = Modifier.padding(spacing.medium),
                                        verticalArrangement = Arrangement.spacedBy(spacing.xSmall),
                                    ) {
                                        Text(
                                            text = stale.choreName,
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Text(
                                            text = when {
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
                onLogCompletion(
                    snapshot.household.id,
                    selectedChoreId!!,
                    selectedMembers.toList(),
                    selectedNote,
                    completedAt,
                )
                selectedChoreId = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogCompletionBottomSheet(
    uiState: DashboardUiState,
    selectedMembers: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    selectedNote: String,
    onNoteChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (kotlin.time.Instant?) -> Unit,
) {
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = kotlin.time.Clock.System.now()
            .toEpochMilliseconds(),
    )
    val selectedDateMillis = datePickerState.selectedDateMillis
    val completedAt = selectedDateMillis
        ?.takeIf { it != midnightUtcToday() }
        ?.let { kotlin.time.Instant.fromEpochMilliseconds(it) }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(text = stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.large)
                .padding(bottom = spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Text(
                text = stringResource(R.string.dashboard_log_completion),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.dashboard_who_completed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                        label = { Text(text = member.displayName) },
                    )
                }
            }
            OutlinedTextField(
                value = selectedNote,
                onValueChange = onNoteChange,
                label = { Text(text = stringResource(R.string.dashboard_note)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    autoCorrectEnabled = true,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                val dateLabel = if (completedAt != null) {
                    formatInstantForLocale(completedAt, "yMMMd")
                } else {
                    stringResource(R.string.dashboard_log_date_today)
                }
                Text(text = stringResource(R.string.dashboard_log_date, dateLabel))
            }
            PrimaryButton(
                text = stringResource(R.string.common_save),
                onClick = { onConfirm(completedAt) },
                enabled = selectedMembers.isNotEmpty(),
            )
        }
    }
}

private const val MILLIS_PER_DAY = 86_400_000L

private fun midnightUtcToday(): Long {
    val ms = kotlin.time.Clock.System.now().toEpochMilliseconds()
    return ms - (ms % MILLIS_PER_DAY)
}

@Composable
private fun RemoteSyncBanner(
    syncState: SyncState,
    onRetrySync: () -> Unit,
) {
    val isError = !syncState.lastErrorMessage.isNullOrBlank()
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Column(
            modifier = Modifier.padding(LocalSpacing.current.medium),
            verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.xSmall),
        ) {
            Text(
                text = stringResource(
                    id = if (isError) {
                        R.string.dashboard_sync_failed_title
                    } else {
                        R.string.dashboard_sync_pending_title
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = syncBannerMessage(syncState),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (syncState.pendingOperations > 0) {
                TextButton(onClick = onRetrySync) {
                    Text(text = stringResource(R.string.common_retry))
                }
            }
        }
    }
}

@Composable
fun RecentCompletionsScreen(
    completions: List<RecentCompletion>,
    onBack: () -> Unit,
    onOpenCompletion: (String) -> Unit,
) {
    val spacing = LocalSpacing.current

    ChoreScaffold(
        topBar = {
            ChoreTopAppBar(
                title = stringResource(R.string.dashboard_all_completions),
                onBackClick = onBack,
            )
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
            if (completions.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.dashboard_recent_completions_empty_title),
                        message = stringResource(R.string.dashboard_recent_completions_empty_message),
                    )
                }
            } else {
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

    ChoreScaffold(
        topBar = {
            ChoreTopAppBar(
                title = completion.choreName,
                onBackClick = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            SectionCard(title = stringResource(R.string.dashboard_completion_detail)) {
                RecentCompletionContent(
                    completion = completion,
                    dateSkeleton = "yMMMdHm",
                )
            }
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
    Text(
        text = completion.choreName,
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = formatInstantForLocale(completion.completedAt, dateSkeleton),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = completion.participantNames.joinToString(),
        style = MaterialTheme.typography.labelLarge,
    )
    completion.note?.takeIf(String::isNotBlank)?.let { note ->
        Text(
            text = note,
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun syncBannerMessage(syncState: SyncState): String {
    val errorMessage = syncState.lastErrorMessage.orEmpty()
    return when {
        errorMessage.contains("Missing or insufficient permissions", ignoreCase = true) ->
            stringResource(R.string.dashboard_sync_failed_permissions)
        errorMessage.isNotBlank() ->
            stringResource(R.string.dashboard_sync_failed_generic)
        else -> stringResource(
            R.string.dashboard_sync_pending_message,
            syncState.pendingOperations,
        )
    }
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
            onLogCompletion = { _, _, _, _, _ -> },
            onRetrySync = {},
            onSeeAllCompletions = {},
            onOpenCompletion = {},
        )
    }
}
