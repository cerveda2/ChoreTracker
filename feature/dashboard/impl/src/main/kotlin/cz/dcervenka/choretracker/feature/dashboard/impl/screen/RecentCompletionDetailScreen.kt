package cz.dcervenka.choretracker.feature.dashboard.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.ChoreTopAppBar
import cz.dcervenka.choretracker.core.design.components.ListGroup
import cz.dcervenka.choretracker.core.design.components.LoadingState
import cz.dcervenka.choretracker.core.design.components.LogCompletionSheet
import cz.dcervenka.choretracker.core.design.rememberSaveableStringList
import cz.dcervenka.choretracker.core.model.stats.RecentCompletion
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.DashboardUiState
import kotlinx.coroutines.flow.Flow

@Composable
fun RecentCompletionDetailScreen(
    completion: RecentCompletion?,
    uiState: DashboardUiState,
    errorEvents: Flow<String>,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (note: String?, participantIds: List<String>) -> Unit,
) {
    val spacing = LocalSpacing.current
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showEditSheet by rememberSaveable { mutableStateOf(false) }
    val editSelectedMembers = rememberSaveableStringList()
    var editNote by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val fallbackErrorMessage = stringResource(R.string.dashboard_completion_action_error)
    LaunchedEffect(errorEvents) {
        errorEvents.collect { message ->
            snackbarHostState.showSnackbar(message.ifBlank { fallbackErrorMessage })
        }
    }

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

    if (showEditSheet) {
        LogCompletionSheet(
            choreName = completion.choreName,
            members = uiState.members,
            selectedMemberIds = editSelectedMembers,
            note = editNote,
            onNoteChange = { editNote = it },
            onDismiss = { showEditSheet = false },
            onConfirm = { _ ->
                onUpdate(editNote.takeIf(String::isNotBlank), editSelectedMembers.toList())
                showEditSheet = false
            },
            editMode = true,
        )
    }

    ChoreScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            ChoreTopAppBar(
                title = completion.choreName,
                onBackClick = onBack,
                actions = {
                    IconButton(onClick = {
                        editNote = completion.note.orEmpty()
                        editSelectedMembers.clear()
                        editSelectedMembers.addAll(completion.participantMemberIds)
                        showEditSheet = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.dashboard_edit_completion),
                        )
                    }
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
            ListGroup {
                Column(
                    modifier = Modifier.padding(spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(spacing.xSmall),
                ) {
                    RecentCompletionContent(
                        completion = completion,
                        dateSkeleton = "yMMMdHm",
                    )
                }
            }
        }
    }
}
