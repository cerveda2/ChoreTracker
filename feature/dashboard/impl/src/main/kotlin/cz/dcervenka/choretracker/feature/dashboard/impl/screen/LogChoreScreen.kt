package cz.dcervenka.choretracker.feature.dashboard.impl.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
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
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.ChoreTopAppBar
import cz.dcervenka.choretracker.core.design.components.EmptyState
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
    onBack: () -> Unit,
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
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + spacing.medium,
                    bottom = innerPadding.calculateBottomPadding() + spacing.large,
                ),
            ) {
                if (suggestedChores.isNotEmpty()) {
                    item(key = "header-suggested") {
                        SectionHeader(label = stringResource(R.string.dashboard_log_chore_suggested))
                    }
                    items(suggestedChores, key = { "suggested-${it.first.id}" }) { (chore, stale) ->
                        val days = stale.daysSinceLastCompletion
                        val hint = if (days != null) {
                            stringResource(R.string.dashboard_days_ago, days)
                        } else {
                            stringResource(R.string.dashboard_never_done)
                        }
                        ChoreRow(
                            name = chore.name,
                            hint = hint,
                            categoryIcon = chore.category.toIcon(),
                            onClick = { openLogSheet(chore.id) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = spacing.large))
                    }
                }

                choresByCategory.forEach { (category, chores) ->
                    item(key = "header-${category.name}") {
                        SectionHeader(
                            label = stringResource(category.toStringRes()),
                            icon = {
                                Icon(
                                    imageVector = category.toIcon(),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                        )
                    }
                    items(chores, key = { it.id }) { chore ->
                        ChoreRow(
                            name = chore.name,
                            onClick = { openLogSheet(chore.id) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = spacing.large))
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

@Composable
private fun SectionHeader(
    label: String,
    icon: (@Composable () -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.large)
            .padding(top = spacing.medium, bottom = spacing.xSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        icon?.invoke()
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ChoreRow(
    name: String,
    onClick: () -> Unit,
    hint: String? = null,
    categoryIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.large, vertical = spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        if (categoryIcon != null) {
            Icon(
                imageVector = categoryIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun ChoreStaleness.urgencyScore(): Double {
    val days = daysSinceLastCompletion ?: return Double.MAX_VALUE
    val freq = frequencyDays
    return if (freq != null && freq > 0) days.toDouble() / freq else days.toDouble() / DEFAULT_ATTENTION_DAYS
}
