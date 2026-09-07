package cz.dcervenka.choretracker.core.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cz.dcervenka.choretracker.core.design.ChoreTrackerTheme
import cz.dcervenka.choretracker.core.design.LocalMemberPalette
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.PreviewData
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.rememberSaveableInstant
import cz.dcervenka.choretracker.core.design.toStringRes
import cz.dcervenka.choretracker.core.formatters.formatInstantForLocale
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogCompletionSheet(
    choreName: String,
    members: List<HouseholdMember>,
    selectedMemberIds: SnapshotStateList<String>,
    note: String,
    onNoteChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (Instant?) -> Unit,
    modifier: Modifier = Modifier,
    editMode: Boolean = false,
    category: ChoreCategory? = null,
    frequencyDays: Int? = null,
    daysSinceLastCompletion: Int? = null,
) {
    val spacing = LocalSpacing.current
    val memberPalette = LocalMemberPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    // null means "use the actual confirm-time instant", not "today at midnight" - only set once
    // the user explicitly picks a day below, so opening (or never touching) the sheet doesn't
    // backdate the completion to whenever the sheet happened to be composed.
    var completedAt by rememberSaveableInstant()
    val timeZone = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(timeZone).date
    val yesterday = today.minus(DatePeriod(days = 1))
    val pickedDate = completedAt?.toLocalDateTime(timeZone)?.date
    val isToday = pickedDate == null || pickedDate == today
    val isYesterday = pickedDate == yesterday
    val isCustomDate = pickedDate != null && !isToday && !isYesterday

    // DatePickerState.selectedDateMillis is UTC midnight of the picked calendar date, not local
    // midnight - passing it straight through as an Instant (or reading one straight back into it)
    // shifts the completion to the wrong local day for any timezone behind UTC. Re-anchor through
    // the local calendar date on both sides of the picker instead.
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = (completedAt ?: Clock.System.now())
            .toLocalDateTime(timeZone).date
            .atStartOfDayIn(TimeZone.UTC)
            .toEpochMilliseconds(),
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        completedAt = datePickerState.selectedDateMillis?.let { millis ->
                            Instant.fromEpochMilliseconds(millis)
                                .toLocalDateTime(TimeZone.UTC).date
                                .atStartOfDayIn(timeZone)
                        }
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
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.large)
                .padding(bottom = spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                    )
                }
                Column {
                    Text(text = choreName, style = MaterialTheme.typography.headlineSmall)
                    val meta = choreMetaLine(
                        category = category,
                        frequencyDays = frequencyDays,
                        daysSinceLastCompletion = daysSinceLastCompletion,
                    )
                    if (meta != null) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.log_sheet_who),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                members.forEachIndexed { index, member ->
                    MemberToggle(
                        label = member.displayName,
                        avatarColor = memberPalette.color(index),
                        selected = member.id in selectedMemberIds,
                        onClick = {
                            if (member.id in selectedMemberIds) {
                                selectedMemberIds.remove(member.id)
                            } else {
                                selectedMemberIds.add(member.id)
                            }
                        },
                    )
                }
                if (members.size > 1) {
                    val allSelected = members.all { it.id in selectedMemberIds }
                    MemberToggle(
                        label = stringResource(R.string.log_sheet_everyone),
                        selected = allSelected,
                        onClick = {
                            if (allSelected) {
                                selectedMemberIds.clear()
                            } else {
                                selectedMemberIds.clear()
                                selectedMemberIds.addAll(members.map { it.id })
                            }
                        },
                    )
                }
            }

            if (!editMode) {
                Text(
                    text = stringResource(R.string.log_sheet_when),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                    ChoreChip(
                        selected = isToday,
                        onClick = { completedAt = null },
                        label = stringResource(R.string.log_sheet_today),
                    )
                    ChoreChip(
                        selected = isYesterday,
                        onClick = { completedAt = yesterday.atStartOfDayIn(timeZone) },
                        label = stringResource(R.string.log_sheet_yesterday),
                    )
                    ChoreChip(
                        selected = isCustomDate,
                        onClick = { showDatePicker = true },
                        label = if (isCustomDate) {
                            formatInstantForLocale(completedAt!!, "yMMMd")
                        } else {
                            stringResource(R.string.log_sheet_pick_date)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                placeholder = { Text(text = stringResource(R.string.log_sheet_note_hint)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    autoCorrectEnabled = true,
                ),
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(spacing.xSmall)) {
                PrimaryButton(
                    text = if (editMode) {
                        stringResource(R.string.common_save)
                    } else {
                        stringResource(R.string.log_sheet_submit, choreName)
                    },
                    onClick = { onConfirm(completedAt) },
                    enabled = selectedMemberIds.isNotEmpty(),
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        }
    }
}

// Returns null (no meta line at all) when there is nothing to show - the edit-completion caller
// has no live chore/staleness data and shouldn't render an empty row.
@Composable
private fun choreMetaLine(
    category: ChoreCategory?,
    frequencyDays: Int?,
    daysSinceLastCompletion: Int?,
): String? {
    if (category == null) return null
    val categoryLabel = stringResource(category.toStringRes())
    val frequency = frequencyDays?.let { days ->
        pluralStringResource(R.plurals.log_sheet_meta_frequency, days, days)
    }
    val lastDone = daysSinceLastCompletion?.let { days ->
        pluralStringResource(R.plurals.log_sheet_meta_last_done, days, days)
    } ?: stringResource(R.string.log_sheet_meta_never_done)
    return listOfNotNull(categoryLabel, frequency, lastDone).joinToString(" · ")
}

@Preview(showBackground = true)
@Composable
private fun LogCompletionSheetPreview() {
    val selectedMemberIds = remember { mutableStateListOf(PreviewData.members.first().id) }
    ChoreTrackerTheme {
        LogCompletionSheet(
            choreName = PreviewData.chores.first().name,
            category = PreviewData.chores.first().category,
            frequencyDays = 6,
            daysSinceLastCompletion = 9,
            members = PreviewData.members,
            selectedMemberIds = selectedMemberIds,
            note = "",
            onNoteChange = {},
            onDismiss = {},
            onConfirm = {},
        )
    }
}
