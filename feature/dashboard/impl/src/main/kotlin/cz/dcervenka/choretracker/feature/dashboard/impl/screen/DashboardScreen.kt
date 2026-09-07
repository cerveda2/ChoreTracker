package cz.dcervenka.choretracker.feature.dashboard.impl.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cz.dcervenka.choretracker.core.design.ChoreTrackerTheme
import cz.dcervenka.choretracker.core.design.LocalMemberPalette
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.PreviewData
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreLargeTopBar
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.EmptyState
import cz.dcervenka.choretracker.core.design.components.ExtendedFabReservedHeight
import cz.dcervenka.choretracker.core.design.components.ExtendedLogFab
import cz.dcervenka.choretracker.core.design.components.ListGroup
import cz.dcervenka.choretracker.core.design.components.LoadingState
import cz.dcervenka.choretracker.core.design.components.LogCompletionSheet
import cz.dcervenka.choretracker.core.design.components.MemberAvatar
import cz.dcervenka.choretracker.core.design.components.PrimaryButton
import cz.dcervenka.choretracker.core.design.components.TopLevelBottomBarSpacer
import cz.dcervenka.choretracker.core.design.rememberSaveableStringList
import cz.dcervenka.choretracker.core.formatters.formatInstantForLocale
import cz.dcervenka.choretracker.core.model.stats.ChoreStatus
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.DashboardUiIntent
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.DashboardUiState
import cz.dcervenka.choretracker.feature.dashboard.impl.viewmodel.UndoEvent
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onIntent: (DashboardUiIntent) -> Unit,
    undoEvents: Flow<UndoEvent>,
    errorEvents: Flow<String>,
    onLogChore: () -> Unit,
    onSeeAllCompletions: () -> Unit,
    onOpenCompletion: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.common_undo)
    val loggedMessage = stringResource(R.string.dashboard_logged_snackbar)
    val fallbackErrorMessage = stringResource(R.string.dashboard_completion_action_error)

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

    LaunchedEffect(errorEvents) {
        errorEvents.collect { message ->
            snackbarHostState.showSnackbar(message.ifBlank { fallbackErrorMessage })
        }
    }

    var selectedChoreId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedNote by rememberSaveable { mutableStateOf("") }
    val selectedMembers = rememberSaveableStringList()
    val currentMemberIndex = uiState.members.indexOfFirst { it.isCurrentUser }
    val currentUserId = uiState.members.getOrNull(currentMemberIndex)?.id
    val openLogSheet: (String) -> Unit = { choreId ->
        selectedChoreId = choreId
        selectedMembers.clear()
        if (currentUserId != null) selectedMembers.add(currentUserId)
        selectedNote = ""
    }
    val quickLog: (String) -> Unit = { choreId ->
        val household = uiState.snapshot?.household
        if (currentUserId != null && household != null) {
            onIntent(
                DashboardUiIntent.LogCompletion(
                    householdId = household.id,
                    choreId = choreId,
                    participantIds = listOf(currentUserId),
                    note = null,
                    completedAt = null,
                ),
            )
        } else {
            openLogSheet(choreId)
        }
    }
    val snapshot = uiState.snapshot

    if (snapshot == null) {
        LoadingState(message = stringResource(R.string.dashboard_loading))
    } else {
        val categoryByChoreId = snapshot.activeChores.associate { it.id to it.category }
        val overdueItems = snapshot.staleChores.filter { it.status == ChoreStatus.NEEDS_ATTENTION }
        val dueSoonItems = snapshot.staleChores.filter {
            it.status == ChoreStatus.SOON || it.status == ChoreStatus.NEVER
        }
        val highlightedCompletions = uiState.allCompletions.take(3)
        val memberIndexById = uiState.members.withIndex().associate { (index, member) -> member.id to index }
        val syncState = uiState.syncState
        val hasSyncIssue = syncState != null &&
            (syncState.pendingOperations > 0 || !syncState.lastErrorMessage.isNullOrBlank())

        ChoreScaffold(
            snackbarHostState = snackbarHostState,
            topBar = {
                ChoreLargeTopBar(
                    title = snapshot.household.name,
                    subtitle = formatInstantForLocale(Clock.System.now(), "EEEEdMMMM"),
                    actions = {
                        IconButton(onClick = { onIntent(DashboardUiIntent.RetrySync) }) {
                            Icon(
                                imageVector = if (hasSyncIssue) Icons.Outlined.CloudOff else Icons.Outlined.CloudDone,
                                contentDescription = stringResource(
                                    if (hasSyncIssue) R.string.dashboard_sync_issue else R.string.dashboard_sync_ok,
                                ),
                                tint = if (hasSyncIssue) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                        if (currentMemberIndex >= 0) {
                            val currentMember = uiState.members[currentMemberIndex]
                            val openSettingsLabel = stringResource(R.string.dashboard_open_settings)
                            MemberAvatar(
                                initial = currentMember.displayName.take(1),
                                color = LocalMemberPalette.current.color(currentMemberIndex),
                                size = 36.dp,
                                modifier = Modifier
                                    .padding(end = spacing.small)
                                    .clickable(onClickLabel = openSettingsLabel, onClick = onOpenSettings),
                            )
                        }
                    },
                )
            },
            floatingActionButton = {
                ExtendedLogFab(text = stringResource(R.string.dashboard_log), onClick = onLogChore)
            },
            bottomBar = { TopLevelBottomBarSpacer() },
        ) { innerPadding ->
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { onIntent(DashboardUiIntent.Refresh) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = spacing.large,
                        top = spacing.large,
                        end = spacing.large,
                        // The FAB floats above TopLevelBottomBarHeight's spacer, not within it -
                        // ExtendedFabReservedHeight keeps the last row (and the FAB itself) clear
                        // of each other rather than overlapping.
                        bottom = innerPadding.calculateBottomPadding() + ExtendedFabReservedHeight + spacing.large,
                    ),
                    verticalArrangement = Arrangement.spacedBy(spacing.large),
                ) {
                    syncState
                        ?.takeIf { it.pendingOperations > 0 || !it.lastErrorMessage.isNullOrBlank() }
                        ?.let { state ->
                            item {
                                RemoteSyncBanner(
                                    syncState = state,
                                    onRetrySync = { onIntent(DashboardUiIntent.RetrySync) },
                                )
                            }
                        }
                    item {
                        BalanceCard(balance = snapshot.balance, members = uiState.members)
                    }
                    if (snapshot.activeChores.isEmpty()) {
                        item {
                            ListGroup {
                                Column(
                                    modifier = Modifier.padding(spacing.medium),
                                    verticalArrangement = Arrangement.spacedBy(spacing.medium),
                                ) {
                                    EmptyState(
                                        title = stringResource(R.string.dashboard_home_empty_title),
                                        message = stringResource(R.string.dashboard_home_empty_message),
                                    )
                                    PrimaryButton(
                                        text = stringResource(R.string.dashboard_add_first_chore),
                                        onClick = onOpenSettings,
                                    )
                                }
                            }
                        }
                    } else {
                        item {
                            OverdueSection(
                                items = overdueItems,
                                categoryByChoreId = categoryByChoreId,
                                onQuickLog = quickLog,
                                onOpenSheet = openLogSheet,
                            )
                        }
                        item {
                            DueSoonSection(
                                items = dueSoonItems,
                                categoryByChoreId = categoryByChoreId,
                                onQuickLog = quickLog,
                                onOpenSheet = openLogSheet,
                            )
                        }
                    }
                    item {
                        RecentActivitySection(
                            completions = highlightedCompletions,
                            hasMore = uiState.allCompletions.size > highlightedCompletions.size,
                            memberIndexById = memberIndexById,
                            onSeeAll = onSeeAllCompletions,
                            onOpenCompletion = onOpenCompletion,
                        )
                    }
                }
            }
        }
    }

    val choreId = selectedChoreId
    if (choreId != null && snapshot != null) {
        val selectedChore = snapshot.activeChores.find { it.id == choreId }
        if (selectedChore != null) {
            val staleness = snapshot.staleChores.find { it.choreId == choreId }
            LogCompletionSheet(
                choreName = selectedChore.name,
                category = selectedChore.category,
                frequencyDays = selectedChore.frequencyDays,
                daysSinceLastCompletion = staleness?.daysSinceLastCompletion,
                members = uiState.members,
                selectedMemberIds = selectedMembers,
                note = selectedNote,
                onNoteChange = { selectedNote = it },
                onDismiss = { selectedChoreId = null },
                onConfirm = { completedAt ->
                    onIntent(
                        DashboardUiIntent.LogCompletion(
                            householdId = snapshot.household.id,
                            choreId = choreId,
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
}

@Preview(showBackground = true, heightDp = 1400)
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
            errorEvents = kotlinx.coroutines.flow.emptyFlow(),
            onLogChore = {},
            onSeeAllCompletions = {},
            onOpenCompletion = {},
            onOpenSettings = {},
        )
    }
}
