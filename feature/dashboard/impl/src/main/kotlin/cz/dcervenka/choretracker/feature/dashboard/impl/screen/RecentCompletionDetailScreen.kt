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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.ChoreTopAppBar
import cz.dcervenka.choretracker.core.design.components.LoadingState
import cz.dcervenka.choretracker.core.design.components.SectionCard
import cz.dcervenka.choretracker.core.model.stats.RecentCompletion
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.DashboardUiState

@Composable
fun RecentCompletionDetailScreen(
    completion: RecentCompletion?,
    uiState: DashboardUiState,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (note: String?, participantIds: List<String>) -> Unit,
) {
    val spacing = LocalSpacing.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    val editSelectedMembers = remember { SnapshotStateList<String>() }
    var editNote by remember { mutableStateOf("") }

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
        LogCompletionBottomSheet(
            uiState = uiState,
            selectedMembers = editSelectedMembers,
            selectedNote = editNote,
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
            SectionCard(title = stringResource(R.string.dashboard_completion_detail)) {
                RecentCompletionContent(
                    completion = completion,
                    dateSkeleton = "yMMMdHm",
                )
            }
        }
    }
}
