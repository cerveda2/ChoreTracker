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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.PrimaryButton
import cz.dcervenka.choretracker.core.formatters.formatInstantForLocale
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.DashboardUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogCompletionBottomSheet(
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
        initialSelectedDateMillis = kotlin.time.Clock.System.now().toEpochMilliseconds(),
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
    val millis = kotlin.time.Clock.System.now().toEpochMilliseconds()
    return millis - (millis % MILLIS_PER_DAY)
}
