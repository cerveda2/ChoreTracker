package cz.dcervenka.choretracker.feature.dashboard.impl.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.DashboardUiIntent
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.DashboardUiState
import cz.dcervenka.choretracker.feature.dashboard.impl.viewmodel.UndoEvent
import kotlinx.coroutines.flow.Flow

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
                choresByCategory.forEach { (category, chores) ->
                    item(key = "header-${category.name}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.large)
                                .padding(top = spacing.medium, bottom = spacing.xSmall),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.small),
                        ) {
                            Icon(
                                imageVector = category.toIcon(),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(category.toStringRes()),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    items(chores, key = { it.id }) { chore ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedChoreId = chore.id
                                    selectedMembers.clear()
                                    if (currentUserId != null) selectedMembers.add(currentUserId)
                                    selectedNote = ""
                                }
                                .padding(horizontal = spacing.large, vertical = spacing.medium),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                        ) {
                            Icon(
                                imageVector = chore.category.toIcon(),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = chore.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                        }
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
