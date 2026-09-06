package cz.dcervenka.choretracker.feature.dashboard.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.PrimaryButton
import cz.dcervenka.choretracker.core.design.rememberSaveableInstant
import cz.dcervenka.choretracker.core.formatters.formatInstantForLocale
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.DashboardUiState
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogCompletionBottomSheet(
    uiState: DashboardUiState,
    selectedMembers: SnapshotStateList<String>,
    selectedNote: String,
    onNoteChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (Instant?) -> Unit,
    editMode: Boolean = false,
) {
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    // null means "use the actual confirm-time instant", not "today at midnight" - only set once
    // the user explicitly confirms a pick in the date dialog below, so opening (or never opening)
    // the sheet doesn't backdate the completion to whenever the sheet happened to be composed.
    var completedAt by rememberSaveableInstant()
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = completedAt?.toEpochMilliseconds() ?: Clock.System.now().toEpochMilliseconds(),
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        completedAt = datePickerState.selectedDateMillis?.let(Instant::fromEpochMilliseconds)
                        showDatePicker = false
                    },
                ) {
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
                text = stringResource(
                    if (editMode) R.string.dashboard_edit_completion else R.string.dashboard_log_completion,
                ),
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
            if (!editMode) {
                TextButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val pickedDate = completedAt
                    val dateLabel = if (pickedDate != null) {
                        formatInstantForLocale(pickedDate, "yMMMd")
                    } else {
                        stringResource(R.string.dashboard_log_date_today)
                    }
                    Text(text = stringResource(R.string.dashboard_log_date, dateLabel))
                }
            }
            PrimaryButton(
                text = stringResource(R.string.common_save),
                onClick = { onConfirm(completedAt) },
                enabled = selectedMembers.isNotEmpty(),
            )
        }
    }
}
