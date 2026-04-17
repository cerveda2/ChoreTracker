package cz.dcervenka.choretracker.feature.dashboard.impl.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import cz.dcervenka.choretracker.core.design.components.LogButton
import cz.dcervenka.choretracker.core.design.components.PrimaryButton
import cz.dcervenka.choretracker.core.design.components.SectionCard
import cz.dcervenka.choretracker.core.formatters.formatInstantForLocale
import cz.dcervenka.choretracker.core.formatters.formatLocalDateForLocale
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.stats.ChoreStatus
import cz.dcervenka.choretracker.core.model.stats.RecentCompletion
import cz.dcervenka.choretracker.core.model.sync.SyncState
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.DashboardUiIntent
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.DashboardUiState
import cz.dcervenka.choretracker.feature.dashboard.impl.viewmodel.UndoEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

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
    val openLogSheet: (choreId: String) -> Unit = { choreId ->
        selectedChoreId = choreId
        selectedMembers.clear()
        if (currentUserId != null) selectedMembers.add(currentUserId)
        selectedNote = ""
    }
    val snapshot = uiState.snapshot
    val quickLog: (choreId: String) -> Unit = { choreId ->
        openLogSheet(choreId)
    }

    if (snapshot == null) {
        LoadingState(message = stringResource(R.string.dashboard_loading))
    } else {
        val quickLogChores = snapshot.activeChores.sortedForQuickLog(uiState.allCompletions).take(8)
        val stalenessByChoreId = snapshot.staleChores.associateBy { it.choreId }
        val highlightedCompletions = uiState.allCompletions.take(3)
        val staleItems = snapshot.staleChores.filter { it.status != ChoreStatus.OK }

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
                                    LogButton(
                                        text = chore.name,
                                        subtitle = subtitle,
                                        onClick = { quickLog(chore.id) },
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
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.padding(spacing.medium),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
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
                    )
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
    val tz = TimeZone.currentSystemDefault()
    val today = remember { Clock.System.now().toLocalDateTime(tz).date }
    val yesterday = remember { today.minus(1, DateTimeUnit.DAY) }
    val todayLabel = stringResource(R.string.dashboard_completions_today)
    val yesterdayLabel = stringResource(R.string.dashboard_completions_yesterday)

    val grouped = remember(completions) {
        completions
            .groupBy { it.completedAt.toLocalDateTime(tz).date }
            .entries
            .sortedByDescending { it.key }
    }

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
                top = innerPadding.calculateTopPadding() + spacing.large,
                bottom = innerPadding.calculateBottomPadding() + spacing.large,
            ),
        ) {
            if (completions.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.dashboard_recent_completions_empty_title),
                        message = stringResource(R.string.dashboard_recent_completions_empty_message),
                    )
                }
            } else {
                grouped.forEach { (date, items) ->
                    val label = when (date) {
                        today -> todayLabel
                        yesterday -> yesterdayLabel
                        else -> formatLocalDateForLocale(date, "EEEMMMd")
                    }
                    item(key = "header-${date}") {
                        CompletionDateHeader(label = label)
                    }
                    itemsIndexed(items, key = { _, completion -> completion.completionId }) { index, completion ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = spacing.medium),
                            )
                        }
                        RecentCompletionRow(
                            completion = completion,
                            onClick = { onOpenCompletion(completion.completionId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletionDateHeader(label: String) {
    val spacing = LocalSpacing.current
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = spacing.medium, vertical = spacing.xSmall),
    )
}

@Composable
fun RecentCompletionDetailScreen(
    completion: RecentCompletion?,
    onBack: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = LocalSpacing.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (completion == null) {
        LoadingState(message = stringResource(R.string.dashboard_completion_loading))
        return
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.dashboard_completion_delete_title)) },
            text = { Text(stringResource(R.string.dashboard_completion_delete_message)) },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    ChoreScaffold(
        topBar = {
            ChoreTopAppBar(
                title = completion.choreName,
                onBackClick = onBack,
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.common_delete),
                        )
                    }
                },
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
    roundedBackground: Boolean = false,
) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (roundedBackground) Modifier.clip(MaterialTheme.shapes.small) else Modifier)
            .clickable(onClick = onClick)
            .padding(
                vertical = if (roundedBackground) spacing.xSmall else spacing.small,
                horizontal = spacing.medium,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = completion.choreName,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = completion.participantNames.joinToString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = formatInstantForLocale(completion.completedAt, "MMMd"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            onIntent = {},
            undoEvents = kotlinx.coroutines.flow.emptyFlow(),
            onSeeAllCompletions = {},
            onOpenCompletion = {},
        )
    }
}
