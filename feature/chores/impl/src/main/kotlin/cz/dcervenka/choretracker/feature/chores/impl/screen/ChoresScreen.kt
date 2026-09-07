package cz.dcervenka.choretracker.feature.chores.impl.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreChip
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.ChoreTopAppBar
import cz.dcervenka.choretracker.core.design.components.EmptyState
import cz.dcervenka.choretracker.core.design.components.ExtendedFabReservedHeight
import cz.dcervenka.choretracker.core.design.components.ExtendedLogFab
import cz.dcervenka.choretracker.core.design.components.ListGroup
import cz.dcervenka.choretracker.core.design.components.LoadingState
import cz.dcervenka.choretracker.core.design.components.SectionHeader
import cz.dcervenka.choretracker.core.design.components.TopLevelBottomBarSpacer
import cz.dcervenka.choretracker.core.design.toStringRes
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
import cz.dcervenka.choretracker.core.model.stats.ChoreStatus
import cz.dcervenka.choretracker.feature.chores.impl.contract.ChoreFilter
import cz.dcervenka.choretracker.feature.chores.impl.contract.ChoresUiEvent
import cz.dcervenka.choretracker.feature.chores.impl.contract.ChoresUiIntent
import cz.dcervenka.choretracker.feature.chores.impl.contract.ChoresUiState
import cz.dcervenka.choretracker.feature.chores.impl.viewmodel.UndoEvent
import kotlinx.coroutines.flow.Flow

@Composable
fun ChoresScreen(
    uiState: ChoresUiState,
    onIntent: (ChoresUiIntent) -> Unit,
    undoEvents: Flow<UndoEvent>,
    events: Flow<ChoresUiEvent>,
) {
    val spacing = LocalSpacing.current
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.common_undo)
    val loggedMessage = stringResource(R.string.dashboard_logged_snackbar)
    val addedMessage = stringResource(R.string.chores_feedback_added)
    val savedMessage = stringResource(R.string.chores_feedback_saved)
    val deletedMessage = stringResource(R.string.chores_feedback_deleted)
    val fallbackErrorMessage = stringResource(R.string.settings_feedback_error)

    LaunchedEffect(undoEvents) {
        undoEvents.collect { event ->
            val result = snackbarHostState.showSnackbar(
                message = loggedMessage.format(event.choreName),
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                onIntent(ChoresUiIntent.DeleteCompletion(event.completionId))
            }
        }
    }

    LaunchedEffect(events) {
        events.collect { event ->
            val message = when (event) {
                ChoresUiEvent.ChoreAdded -> addedMessage
                ChoresUiEvent.ChoreSaved -> savedMessage
                ChoresUiEvent.ChoreDeleted -> deletedMessage
                is ChoresUiEvent.Error -> event.message.ifBlank { fallbackErrorMessage }
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var editingChoreId by rememberSaveable { mutableStateOf<String?>(null) }
    var showNewChoreSheet by rememberSaveable { mutableStateOf(false) }
    val currentUserId = uiState.members.firstOrNull { it.isCurrentUser }?.id
    val overdueCount = uiState.chores.count { chore ->
        chore.isActive && uiState.staleness[chore.id]?.status == ChoreStatus.NEEDS_ATTENTION
    }

    val quickLog: (Chore) -> Unit = { chore ->
        val householdId = uiState.householdId
        if (householdId != null && currentUserId != null) {
            onIntent(
                ChoresUiIntent.LogCompletion(
                    householdId = householdId,
                    choreId = chore.id,
                    participantIds = listOf(currentUserId),
                    note = null,
                    completedAt = null,
                ),
            )
        } else {
            editingChoreId = chore.id
        }
    }

    ChoreScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            ChoreTopAppBar(
                title = stringResource(R.string.chores_title),
                actions = {
                    IconButton(onClick = { searchExpanded = !searchExpanded }) {
                        Icon(
                            imageVector = if (searchExpanded) Icons.Outlined.Close else Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.chores_search_hint),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedLogFab(text = stringResource(R.string.chores_new_chore), onClick = { showNewChoreSheet = true })
        },
        bottomBar = { TopLevelBottomBarSpacer() },
    ) { innerPadding ->
        if (uiState.householdId == null) {
            LoadingState(
                message = stringResource(R.string.dashboard_loading),
                modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
            ) {
                if (searchExpanded) {
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = { onIntent(ChoresUiIntent.QueryChanged(it)) },
                        label = { Text(stringResource(R.string.chores_search_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.large, vertical = spacing.small),
                    )
                }
                // Always visible, independent of whether the current filter/search has any
                // matches - otherwise picking a filter with zero results makes the chips
                // themselves vanish along with the list, with no way back except clearing state.
                ChoreFilterRow(
                    filter = uiState.filter,
                    overdueCount = overdueCount,
                    onFilterChanged = { onIntent(ChoresUiIntent.FilterChanged(it)) },
                    modifier = Modifier.padding(horizontal = spacing.large, vertical = spacing.small),
                )
                val groups = groupChores(uiState)
                if (groups.isEmpty()) {
                    val (emptyTitle, emptyMessage) = if (uiState.chores.isEmpty()) {
                        stringResource(R.string.chores_empty_title) to stringResource(R.string.chores_empty_message)
                    } else {
                        stringResource(R.string.chores_filter_empty_title) to
                            stringResource(R.string.chores_filter_empty_message)
                    }
                    EmptyState(
                        title = emptyTitle,
                        message = emptyMessage,
                        modifier = Modifier.padding(spacing.large),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = spacing.large,
                            top = spacing.small,
                            end = spacing.large,
                            bottom = innerPadding.calculateBottomPadding() + ExtendedFabReservedHeight + spacing.large,
                        ),
                        verticalArrangement = Arrangement.spacedBy(spacing.large),
                    ) {
                        groups.forEach { (category, chores) ->
                            item(key = "group-${category.name}") {
                                Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                                    SectionHeader(title = stringResource(category.toStringRes()))
                                    ListGroup {
                                        chores.forEachIndexed { index, chore ->
                                            if (index > 0) {
                                                HorizontalDivider(
                                                    modifier = Modifier.padding(horizontal = spacing.medium),
                                                )
                                            }
                                            ChoreRow(
                                                chore = chore,
                                                staleness = uiState.staleness[chore.id],
                                                onQuickLog = { quickLog(chore) },
                                                onOpenSheet = { editingChoreId = chore.id },
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
    }

    val editingChore = uiState.chores.find { it.id == editingChoreId }
    if (editingChore != null) {
        ChoreEditorSheet(
            chore = editingChore,
            isOwner = uiState.isOwner,
            onDismiss = { editingChoreId = null },
            onSave = { name, category, frequencyDays, isActive ->
                onIntent(
                    ChoresUiIntent.UpdateChore(
                        choreId = editingChore.id,
                        name = name,
                        category = category,
                        frequencyDays = frequencyDays,
                        isActive = isActive,
                    ),
                )
                editingChoreId = null
            },
            onDelete = {
                onIntent(ChoresUiIntent.DeleteChore(editingChore.id))
                editingChoreId = null
            },
        )
    }

    val householdId = uiState.householdId
    if (showNewChoreSheet && householdId != null) {
        ChoreEditorSheet(
            chore = null,
            isOwner = uiState.isOwner,
            onDismiss = { showNewChoreSheet = false },
            onSave = { name, category, frequencyDays, _ ->
                onIntent(
                    ChoresUiIntent.AddChore(
                        householdId = householdId,
                        name = name,
                        category = category,
                        frequencyDays = frequencyDays,
                    ),
                )
                showNewChoreSheet = false
            },
        )
    }
}

@Composable
private fun ChoreFilterRow(
    filter: ChoreFilter,
    overdueCount: Int,
    onFilterChanged: (ChoreFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        ChoreChip(
            selected = filter == ChoreFilter.ALL,
            onClick = { onFilterChanged(ChoreFilter.ALL) },
            label = stringResource(R.string.chores_filter_all),
        )
        ChoreChip(
            selected = filter == ChoreFilter.OVERDUE,
            onClick = { onFilterChanged(ChoreFilter.OVERDUE) },
            label = if (overdueCount > 0) {
                stringResource(R.string.chores_filter_overdue_count, overdueCount)
            } else {
                stringResource(R.string.chores_filter_overdue)
            },
        )
        ChoreChip(
            selected = filter == ChoreFilter.DUE_SOON,
            onClick = { onFilterChanged(ChoreFilter.DUE_SOON) },
            label = stringResource(R.string.chores_filter_due_soon),
        )
        ChoreChip(
            selected = filter == ChoreFilter.PAUSED,
            onClick = { onFilterChanged(ChoreFilter.PAUSED) },
            label = stringResource(R.string.chores_filter_paused),
        )
    }
}

private fun groupChores(uiState: ChoresUiState): List<Pair<ChoreCategory, List<Chore>>> {
    val base = if (uiState.query.isBlank()) {
        uiState.chores
    } else {
        uiState.chores.filter { it.name.contains(uiState.query, ignoreCase = true) }
    }
    val filtered = when (uiState.filter) {
        ChoreFilter.ALL -> base
        ChoreFilter.OVERDUE -> base.filter {
            it.isActive && uiState.staleness[it.id]?.status == ChoreStatus.NEEDS_ATTENTION
        }
        ChoreFilter.DUE_SOON -> base.filter {
            it.isActive &&
                uiState.staleness[it.id]?.status in setOf(ChoreStatus.SOON, ChoreStatus.NEVER)
        }
        ChoreFilter.PAUSED -> base.filter { !it.isActive }
    }
    return filtered
        .groupBy { it.category }
        .toSortedMap(compareBy { it.ordinal })
        .map { (category, chores) -> category to chores.sortedBy { it.name } }
}
