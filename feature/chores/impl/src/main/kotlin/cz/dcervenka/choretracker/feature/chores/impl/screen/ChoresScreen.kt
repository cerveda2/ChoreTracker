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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import cz.dcervenka.choretracker.core.common.performance.PerformanceTraceName
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
import cz.dcervenka.choretracker.core.design.components.LogCompletionSheet
import cz.dcervenka.choretracker.core.design.components.OutOfTurnDialog
import cz.dcervenka.choretracker.core.design.components.SectionHeader
import cz.dcervenka.choretracker.core.design.components.TopLevelBottomBarSpacer
import cz.dcervenka.choretracker.core.design.performance.TrackScreenLoad
import cz.dcervenka.choretracker.core.design.rememberSaveableStringList
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
    TrackScreenLoad(PerformanceTraceName.CHORES_LOAD, isLoading = uiState.householdId == null)
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.common_undo)
    val loggedMessage = stringResource(R.string.dashboard_logged_snackbar)
    val addedMessage = stringResource(R.string.chores_feedback_added)
    val savedMessage = stringResource(R.string.chores_feedback_saved)
    val deletedMessage = stringResource(R.string.chores_feedback_deleted)
    val askedMessage = stringResource(R.string.chores_ask_sent)
    val resources = LocalResources.current

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
                is ChoresUiEvent.RequestsSent -> askedMessage.format(event.memberName)
                is ChoresUiEvent.Error -> resources.getString(event.error.toStringRes())
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var editingChoreId by rememberSaveable { mutableStateOf<String?>(null) }
    var showNewChoreSheet by rememberSaveable { mutableStateOf(false) }
    var newChoreReminderEnabled by rememberSaveable { mutableStateOf(true) }
    var logChoreId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAskSheet by rememberSaveable { mutableStateOf(false) }
    val askableMembers = uiState.members.filter { it.removedAt == null && it.userId != null && !it.isCurrentUser }
    var logNote by rememberSaveable { mutableStateOf("") }
    val logMembers = rememberSaveableStringList()
    val currentUserId = uiState.members.firstOrNull { it.isCurrentUser }?.id
    val overdueCount = uiState.chores.count { chore ->
        chore.isActive && uiState.staleness[chore.id]?.status == ChoreStatus.NEEDS_ATTENTION
    }

    val openLogSheet: (String) -> Unit = { choreId ->
        logChoreId = choreId
        logMembers.clear()
        if (currentUserId != null) logMembers.add(currentUserId)
        logNote = ""
    }

    val expandedGroups = rememberSaveableStringList()
    // Searching or filtering opens every group, so a match is never hidden inside a folded one.
    val expandAllGroups = uiState.query.isNotBlank() || uiState.filter != ChoreFilter.ALL
    val groupNames = uiState.chores.mapNotNull { it.groupName }.distinct().sorted()

    var outOfTurnChoreId by rememberSaveable { mutableStateOf<String?>(null) }
    val logNow: (Chore) -> Unit = { chore ->
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
            // Members not yet loaded, or the current user's own membership row momentarily
            // absent - open the log-completion sheet (like the dashboard's quickLog fallback),
            // not the chore editor, so this window can't be mistaken for a rename/frequency
            // prompt.
            openLogSheet(chore.id)
        }
    }
    val quickLog: (Chore) -> Unit = { chore ->
        val turn = uiState.staleness[chore.id]?.turn
        if (turn != null && !turn.isCurrentUser) outOfTurnChoreId = chore.id else logNow(chore)
    }

    ChoreScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            ChoreTopAppBar(
                title = stringResource(R.string.chores_title),
                actions = {
                    if (askableMembers.isNotEmpty() && uiState.chores.any { it.isActive && !it.isPersonal }) {
                        TextButton(onClick = { showAskSheet = true }) {
                            Text(stringResource(R.string.chores_ask_action))
                        }
                    }
                    IconButton(
                        onClick = {
                            searchExpanded = !searchExpanded
                            // Collapsing without clearing would leave the list silently filtered
                            // by a query the user can no longer see or edit.
                            if (!searchExpanded) onIntent(ChoresUiIntent.QueryChanged(""))
                        },
                    ) {
                        Icon(
                            imageVector = if (searchExpanded) Icons.Outlined.Close else Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.chores_search_hint),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedLogFab(
                text = stringResource(R.string.chores_new_chore),
                onClick = {
                    newChoreReminderEnabled = true
                    showNewChoreSheet = true
                },
            )
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
                        groups.forEach { (category, entries) ->
                            item(key = "group-${category?.name ?: "personal"}") {
                                Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                                    val title = category?.toStringRes() ?: R.string.chore_personal_label
                                    SectionHeader(title = stringResource(title))
                                    ListGroup {
                                        @Composable
                                        fun Item(chore: Chore, modifier: Modifier = Modifier) = ChoreRow(
                                            chore = chore,
                                            staleness = uiState.staleness[chore.id],
                                            onQuickLog = { quickLog(chore) },
                                            onOpenSheet = { editingChoreId = chore.id },
                                            modifier = modifier,
                                            reminderMuted = uiState.remindersEnabled &&
                                                chore.isActive &&
                                                chore.id in uiState.mutedReminderChoreIds,
                                        )
                                        entries.forEachIndexed { index, entry ->
                                            if (index > 0) {
                                                HorizontalDivider(
                                                    modifier = Modifier.padding(horizontal = spacing.medium),
                                                )
                                            }
                                            when (entry) {
                                                is ChoreListEntry.Single -> Item(entry.chore)
                                                is ChoreListEntry.Group -> {
                                                    val expanded = expandAllGroups || entry.name in expandedGroups
                                                    ChoreGroupRow(
                                                        name = entry.name,
                                                        chores = entry.chores,
                                                        staleness = uiState.staleness,
                                                        expanded = expanded,
                                                        onToggle = if (expandAllGroups) {
                                                            null
                                                        } else {
                                                            {
                                                                if (entry.name in expandedGroups) {
                                                                    expandedGroups.remove(entry.name)
                                                                } else {
                                                                    expandedGroups.add(entry.name)
                                                                }
                                                            }
                                                        },
                                                    )
                                                    if (expanded) {
                                                        entry.chores.forEach { chore ->
                                                            HorizontalDivider(
                                                                modifier = Modifier.padding(
                                                                    start = spacing.xLarge,
                                                                    end = spacing.medium,
                                                                ),
                                                            )
                                                            Item(chore, Modifier.padding(start = spacing.large))
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
                }
            }
        }
    }

    val activeMembers = uiState.members.filter { it.removedAt == null }
    val editingChore = uiState.chores.find { it.id == editingChoreId }
    if (editingChore != null) {
        ChoreEditorSheet(
            chore = editingChore,
            canEdit = uiState.isManager ||
                (uiState.currentUserId != null && editingChore.createdByUserId == uiState.currentUserId),
            canDelete = uiState.isManager ||
                (
                    uiState.currentUserId != null && editingChore.createdByUserId == uiState.currentUserId &&
                        editingChore.id !in uiState.completedChoreIds
                    ),
            members = activeMembers,
            groupNames = groupNames,
            onDismiss = { editingChoreId = null },
            onSave = { name, category, schedule, isActive, rotationMemberIds, groupName, _ ->
                onIntent(
                    ChoresUiIntent.UpdateChore(
                        choreId = editingChore.id,
                        name = name,
                        category = category,
                        schedule = schedule,
                        isActive = isActive,
                        rotationMemberIds = rotationMemberIds,
                        groupName = groupName,
                    ),
                )
                editingChoreId = null
            },
            onDelete = {
                onIntent(ChoresUiIntent.DeleteChore(editingChore.id))
                editingChoreId = null
            },
            reminderEnabled = (editingChore.id !in uiState.mutedReminderChoreIds)
                .takeIf { uiState.remindersEnabled },
            onReminderChange = { enabled -> onIntent(ChoresUiIntent.SetChoreReminder(editingChore.id, enabled)) },
        )
    }

    val householdId = uiState.householdId
    if (showNewChoreSheet && householdId != null) {
        ChoreEditorSheet(
            chore = null,
            canEdit = true,
            members = activeMembers,
            groupNames = groupNames,
            onDismiss = { showNewChoreSheet = false },
            onSave = { name, category, schedule, _, rotationMemberIds, groupName, personal ->
                onIntent(
                    ChoresUiIntent.AddChore(
                        householdId = householdId,
                        name = name,
                        category = category,
                        schedule = schedule,
                        reminderEnabled = newChoreReminderEnabled,
                        rotationMemberIds = rotationMemberIds,
                        groupName = groupName,
                        personal = personal,
                    ),
                )
                showNewChoreSheet = false
            },
            reminderEnabled = newChoreReminderEnabled.takeIf { uiState.remindersEnabled },
            onReminderChange = { newChoreReminderEnabled = it },
        )
    }

    val outOfTurnChore = uiState.chores.find { it.id == outOfTurnChoreId }
    val outOfTurn = outOfTurnChore?.let { uiState.staleness[it.id]?.turn }
    if (outOfTurnChore != null && outOfTurn != null) {
        OutOfTurnDialog(
            turn = outOfTurn,
            onConfirm = {
                logNow(outOfTurnChore)
                outOfTurnChoreId = null
            },
            onDismiss = { outOfTurnChoreId = null },
        )
    }

    if (showAskSheet && householdId != null) {
        AskSheet(
            members = askableMembers,
            chores = uiState.chores.filter { it.isActive && !it.isPersonal },
            onDismiss = { showAskSheet = false },
            onSend = { toMemberId, choreIds, note ->
                askableMembers.find { it.id == toMemberId }?.let { member ->
                    onIntent(ChoresUiIntent.SendRequests(householdId, member, choreIds, note))
                }
                showAskSheet = false
            },
        )
    }

    val loggingChoreId = logChoreId
    if (loggingChoreId != null && householdId != null) {
        val loggingChore = uiState.chores.find { it.id == loggingChoreId }
        if (loggingChore != null) {
            LogCompletionSheet(
                choreName = loggingChore.name,
                category = loggingChore.category,
                schedule = loggingChore.schedule,
                daysSinceLastCompletion = uiState.staleness[loggingChoreId]?.daysSinceLastCompletion,
                members = if (loggingChore.isPersonal) emptyList() else uiState.members,
                selectedMemberIds = logMembers,
                note = logNote,
                onNoteChange = { logNote = it },
                onDismiss = { logChoreId = null },
                onConfirm = { completedAt ->
                    onIntent(
                        ChoresUiIntent.LogCompletion(
                            householdId = householdId,
                            choreId = loggingChoreId,
                            participantIds = logMembers.toList(),
                            note = logNote,
                            completedAt = completedAt,
                        ),
                    )
                    logChoreId = null
                },
            )
        }
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

private sealed interface ChoreListEntry {
    val sortName: String

    data class Single(val chore: Chore) : ChoreListEntry {
        override val sortName get() = chore.name
    }

    data class Group(val name: String, val chores: List<Chore>) : ChoreListEntry {
        override val sortName get() = name
    }
}

// A null category is the section of the user's personal chores, listed first.
private fun groupChores(uiState: ChoresUiState): List<Pair<ChoreCategory?, List<ChoreListEntry>>> {
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
    // A group sits in the section most of its chores belong to, counting the ones filtered out too, so
    // filtering doesn't move it.
    val groupCategories = uiState.chores.byGroup().mapValues { (_, chores) ->
        val counts = chores.groupingBy { it.category }.eachCount()
        ChoreCategory.entries.maxBy { counts[it] ?: 0 }
    }
    val (personal, household) = filtered.partition { it.isPersonal }
    val singles = household.filter { it.groupName == null }
        .map { it.category to ChoreListEntry.Single(it) }
    val groups = household.byGroup()
        .map { (name, chores) -> groupCategories.getValue(name) to ChoreListEntry.Group(name, chores.sortedBy { it.name }) }
    val sections = (singles + groups)
        .groupBy({ it.first }, { it.second })
        .toSortedMap(compareBy { it.ordinal })
        .map { (category, entries) -> category to entries.sortedBy { it.sortName } }
    return if (personal.isEmpty()) {
        sections
    } else {
        listOf(null to personal.sortedBy { it.name }.map(ChoreListEntry::Single)) + sections
    }
}

private fun List<Chore>.byGroup(): Map<String, List<Chore>> =
    mapNotNull { chore -> chore.groupName?.let { it to chore } }.groupBy({ it.first }, { it.second })
