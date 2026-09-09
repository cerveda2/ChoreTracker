package cz.dcervenka.choretracker.feature.dashboard.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import cz.dcervenka.choretracker.core.design.components.ChoreListRow
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.ChoreTopAppBar
import cz.dcervenka.choretracker.core.design.components.EmptyState
import cz.dcervenka.choretracker.core.design.components.IconCircle
import cz.dcervenka.choretracker.core.design.components.ListGroup
import cz.dcervenka.choretracker.core.design.components.LogCompletionSheet
import cz.dcervenka.choretracker.core.design.components.SectionHeader
import cz.dcervenka.choretracker.core.design.rememberSaveableStringList
import cz.dcervenka.choretracker.core.design.toIcon
import cz.dcervenka.choretracker.core.design.toStringRes
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
import cz.dcervenka.choretracker.core.model.stats.ChoreStaleness
import cz.dcervenka.choretracker.core.model.stats.ChoreStatus
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.DashboardUiIntent
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.DashboardUiState
import cz.dcervenka.choretracker.feature.dashboard.impl.viewmodel.UndoEvent
import kotlinx.coroutines.flow.Flow

private const val SUGGESTION_LIMIT = 5
private const val DEFAULT_ATTENTION_DAYS = 14.0

@Composable
fun LogChoreScreen(
    uiState: DashboardUiState,
    onIntent: (DashboardUiIntent) -> Unit,
    undoEvents: Flow<UndoEvent>,
    errorEvents: Flow<String>,
    onBack: () -> Unit,
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
    val currentUserId = uiState.members.firstOrNull { it.isCurrentUser }?.id
    val snapshot = uiState.snapshot

    val activeChores = snapshot?.activeChores.orEmpty()
    val choresByCategory = ChoreCategory.entries
        .mapNotNull { category ->
            val chores = activeChores.filter { it.category == category }.sortedBy { it.name }
            if (chores.isNotEmpty()) category to chores else null
        }

    val staleByChoreId = snapshot?.staleChores?.associateBy { it.choreId }.orEmpty()
    val suggestedChores = activeChores
        .mapNotNull { chore -> staleByChoreId[chore.id]?.let { stale -> chore to stale } }
        .filter { (_, stale) -> stale.status != ChoreStatus.OK }
        .sortedByDescending { (_, stale) -> stale.urgencyScore() }
        .take(SUGGESTION_LIMIT)

    val openLogSheet: (String) -> Unit = { choreId ->
        selectedChoreId = choreId
        selectedMembers.clear()
        if (currentUserId != null) selectedMembers.add(currentUserId)
        selectedNote = ""
    }

    ChoreScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            ChoreTopAppBar(
                title = stringResource(R.string.dashboard_log_chore_title),
                onBackClick = onBack,
            )
        },
    ) { innerPadding ->
        if (choresByCategory.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.dashboard_quick_log_empty_title),
                message = stringResource(R.string.dashboard_quick_log_empty_message),
                modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = spacing.large,
                    top = innerPadding.calculateTopPadding() + spacing.medium,
                    end = spacing.large,
                    bottom = innerPadding.calculateBottomPadding() + spacing.large,
                ),
                verticalArrangement = Arrangement.spacedBy(spacing.large),
            ) {
                if (suggestedChores.isNotEmpty()) {
                    item(key = "group-suggested") {
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                            SectionHeader(title = stringResource(R.string.dashboard_log_chore_suggested))
                            ListGroup {
                                suggestedChores.forEachIndexed { index, (chore, stale) ->
                                    if (index > 0) {
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = spacing.medium))
                                    }
                                    val days = stale.daysSinceLastCompletion
                                    val hint = if (days != null) {
                                        stringResource(R.string.dashboard_days_ago, days)
                                    } else {
                                        stringResource(R.string.dashboard_never_done)
                                    }
                                    ChoreListRow(
                                        leading = { IconCircle(icon = chore.category.toIcon()) },
                                        title = chore.name,
                                        subtitle = hint,
                                        onClick = { openLogSheet(chore.id) },
                                    )
                                }
                            }
                        }
                    }
                }

                choresByCategory.forEach { (category, chores) ->
                    item(key = "group-${category.name}") {
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                            SectionHeader(title = stringResource(category.toStringRes()))
                            ListGroup {
                                chores.forEachIndexed { index, chore ->
                                    if (index > 0) {
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = spacing.medium))
                                    }
                                    ChoreListRow(
                                        leading = { IconCircle(icon = category.toIcon()) },
                                        title = chore.name,
                                        onClick = { openLogSheet(chore.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val choreId = selectedChoreId
    if (choreId != null && snapshot != null) {
        val selectedChore = activeChores.find { it.id == choreId }
        if (selectedChore != null) {
            LogCompletionSheet(
                choreName = selectedChore.name,
                category = selectedChore.category,
                frequencyDays = selectedChore.frequencyDays,
                daysSinceLastCompletion = staleByChoreId[choreId]?.daysSinceLastCompletion,
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

private fun ChoreStaleness.urgencyScore(): Double {
    val days = daysSinceLastCompletion ?: return Double.MAX_VALUE
    val freq = frequencyDays
    return if (freq != null && freq > 0) days.toDouble() / freq else days.toDouble() / DEFAULT_ATTENTION_DAYS
}
