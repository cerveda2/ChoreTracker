package cz.dcervenka.choretracker.feature.chores.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreChip
import cz.dcervenka.choretracker.core.design.components.PrimaryButton
import cz.dcervenka.choretracker.core.design.components.shortName
import cz.dcervenka.choretracker.core.design.components.weekdaysInOrder
import cz.dcervenka.choretracker.core.design.suggestions
import cz.dcervenka.choretracker.core.design.toIcon
import cz.dcervenka.choretracker.core.design.toStringRes
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
import cz.dcervenka.choretracker.core.model.chore.ChoreSchedule
import cz.dcervenka.choretracker.core.model.chore.isValid
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber

private val PRESET_FREQUENCY_DAYS = listOf(1, 2, 3, 7)
private const val SHORTEST_MONTH_DAYS = 28

private enum class ScheduleKind { PRESET, CUSTOM, WEEKDAYS, MONTH_DAY }

private fun ChoreSchedule?.toKind(): ScheduleKind = when (this) {
    null -> ScheduleKind.PRESET
    is ChoreSchedule.EveryDays -> if (days in PRESET_FREQUENCY_DAYS) ScheduleKind.PRESET else ScheduleKind.CUSTOM
    is ChoreSchedule.Weekdays -> ScheduleKind.WEEKDAYS
    is ChoreSchedule.MonthDay -> ScheduleKind.MONTH_DAY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChoreEditorSheet(
    chore: Chore?,
    canEdit: Boolean,
    canDelete: Boolean = false,
    onDismiss: () -> Unit,
    members: List<HouseholdMember>,
    onSave: (
        name: String,
        category: ChoreCategory,
        schedule: ChoreSchedule?,
        isActive: Boolean,
        rotationMemberIds: List<String>,
    ) -> Unit,
    onDelete: (() -> Unit)? = null,
    reminderEnabled: Boolean? = null,
    onReminderChange: (Boolean) -> Unit = {},
) {
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by rememberSaveable(chore?.id) { mutableStateOf(chore?.name.orEmpty()) }
    var category by rememberSaveable(chore?.id) { mutableStateOf(chore?.category ?: ChoreCategory.OTHER) }
    val initialSchedule = chore?.schedule
    val initialDays = (initialSchedule as? ChoreSchedule.EveryDays)?.days
    var scheduleKind by rememberSaveable(chore?.id) { mutableStateOf(initialSchedule.toKind()) }
    var presetFrequencyDays by rememberSaveable(chore?.id) {
        mutableStateOf(initialDays?.takeIf { it in PRESET_FREQUENCY_DAYS })
    }
    var customFrequencyInput by rememberSaveable(chore?.id) {
        mutableStateOf(initialDays?.takeIf { it !in PRESET_FREQUENCY_DAYS }?.toString().orEmpty())
    }
    var weekdayNumbers by rememberSaveable(chore?.id) {
        mutableStateOf(ArrayList((initialSchedule as? ChoreSchedule.Weekdays)?.days.orEmpty().map { it.isoDayNumber }))
    }
    var monthDayInput by rememberSaveable(chore?.id) {
        mutableStateOf((initialSchedule as? ChoreSchedule.MonthDay)?.day?.toString().orEmpty())
    }
    var isActive by rememberSaveable(chore?.id) { mutableStateOf(chore?.isActive ?: true) }
    // Existing members first in their stored order, newly picked ones after them in tap order. Removed
    // members stay in the list unseen, so saving an unrelated change doesn't restart the count.
    var rotation by rememberSaveable(chore?.id) {
        mutableStateOf(ArrayList(chore?.rotationMemberIds.orEmpty()))
    }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    val schedule = when (scheduleKind) {
        ScheduleKind.PRESET -> presetFrequencyDays?.let(ChoreSchedule::EveryDays)
        ScheduleKind.CUSTOM -> customFrequencyInput.toIntOrNull()?.let(ChoreSchedule::EveryDays)
        // Every day of the week is the same as daily, which reads better on rows than all seven days.
        ScheduleKind.WEEKDAYS -> if (weekdayNumbers.size == DayOfWeek.entries.size) {
            ChoreSchedule.EveryDays(1)
        } else {
            ChoreSchedule.Weekdays(weekdayNumbers.mapTo(mutableSetOf()) { DayOfWeek(it) })
        }
        ScheduleKind.MONTH_DAY -> monthDayInput.toIntOrNull()?.let(ChoreSchedule::MonthDay)
    }?.takeIf { it.isValid }
    // "None" is a preset with no days; every other kind needs a valid value before saving.
    val scheduleComplete = scheduleKind == ScheduleKind.PRESET || schedule != null

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.chores_delete_title)) },
            text = { Text(stringResource(R.string.chores_delete_message)) },
            confirmButton = {
                TextButton(onClick = { onDelete?.invoke() }) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.large)
                .padding(bottom = spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Text(
                text = chore?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chores_new_chore),
                style = MaterialTheme.typography.headlineSmall,
            )
            if (!canEdit) {
                Text(
                    text = stringResource(R.string.chores_editor_read_only),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.chores_editor_name_hint)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    autoCorrectEnabled = true,
                ),
                enabled = canEdit,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.chores_editor_category_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                ChoreCategory.entries.forEach { entry ->
                    ChoreChip(
                        selected = category == entry,
                        onClick = { category = entry },
                        label = stringResource(entry.toStringRes()),
                        enabled = canEdit,
                        leadingIcon = { Icon(imageVector = entry.toIcon(), contentDescription = null) },
                    )
                }
            }
            if (name.isBlank()) {
                val suggestions = category.suggestions()
                if (suggestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                        suggestions.forEach { suggestionRes ->
                            val suggestion = stringResource(suggestionRes)
                            ChoreChip(
                                selected = false,
                                onClick = { name = suggestion },
                                label = suggestion,
                                enabled = canEdit,
                            )
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.chores_editor_frequency_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val selectPreset: (Int?) -> Unit = { days ->
                scheduleKind = ScheduleKind.PRESET
                presetFrequencyDays = days
            }
            val isPreset = scheduleKind == ScheduleKind.PRESET
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                ChoreChip(
                    selected = isPreset && presetFrequencyDays == null,
                    onClick = { selectPreset(null) },
                    label = stringResource(R.string.chores_editor_frequency_none),
                    enabled = canEdit,
                )
                ChoreChip(
                    selected = isPreset && presetFrequencyDays == 1,
                    onClick = { selectPreset(1) },
                    label = stringResource(R.string.chores_editor_frequency_daily),
                    enabled = canEdit,
                )
                ChoreChip(
                    selected = isPreset && presetFrequencyDays == 2,
                    onClick = { selectPreset(2) },
                    label = pluralStringResource(R.plurals.log_sheet_meta_frequency, 2, 2),
                    enabled = canEdit,
                )
                ChoreChip(
                    selected = isPreset && presetFrequencyDays == 3,
                    onClick = { selectPreset(3) },
                    label = pluralStringResource(R.plurals.log_sheet_meta_frequency, 3, 3),
                    enabled = canEdit,
                )
                ChoreChip(
                    selected = isPreset && presetFrequencyDays == 7,
                    onClick = { selectPreset(7) },
                    label = stringResource(R.string.chores_editor_frequency_weekly),
                    enabled = canEdit,
                )
                ChoreChip(
                    selected = scheduleKind == ScheduleKind.CUSTOM,
                    onClick = { scheduleKind = ScheduleKind.CUSTOM },
                    label = stringResource(R.string.chores_editor_frequency_custom),
                    enabled = canEdit,
                )
                ChoreChip(
                    selected = scheduleKind == ScheduleKind.WEEKDAYS,
                    onClick = { scheduleKind = ScheduleKind.WEEKDAYS },
                    label = stringResource(R.string.chores_editor_frequency_weekdays),
                    enabled = canEdit,
                )
                ChoreChip(
                    selected = scheduleKind == ScheduleKind.MONTH_DAY,
                    onClick = { scheduleKind = ScheduleKind.MONTH_DAY },
                    label = stringResource(R.string.chores_editor_frequency_month_day),
                    enabled = canEdit,
                )
            }
            when (scheduleKind) {
                ScheduleKind.PRESET -> Unit
                ScheduleKind.CUSTOM -> OutlinedTextField(
                    value = customFrequencyInput,
                    onValueChange = { customFrequencyInput = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.chores_editor_frequency_custom_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = canEdit,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ScheduleKind.WEEKDAYS -> WeekdayChips(
                    selected = weekdayNumbers,
                    onToggle = { day ->
                        val toggled = if (day in weekdayNumbers) weekdayNumbers - day else weekdayNumbers + day
                        weekdayNumbers = ArrayList(toggled)
                    },
                    enabled = canEdit,
                )
                ScheduleKind.MONTH_DAY -> OutlinedTextField(
                    value = monthDayInput,
                    onValueChange = { monthDayInput = it.filter(Char::isDigit).take(2) },
                    label = { Text(stringResource(R.string.chores_editor_frequency_month_day_hint)) },
                    supportingText = if (((schedule as? ChoreSchedule.MonthDay)?.day ?: 0) > SHORTEST_MONTH_DAYS) {
                        { Text(stringResource(R.string.chores_editor_frequency_month_day_short_months)) }
                    } else {
                        null
                    },
                    isError = monthDayInput.isNotEmpty() && schedule == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = canEdit,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (members.size > 1) {
                Text(
                    text = stringResource(R.string.chores_editor_turns_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                    members.forEach { member ->
                        ChoreChip(
                            selected = member.id in rotation,
                            onClick = {
                                val picked = if (member.id in rotation) rotation - member.id else rotation + member.id
                                rotation = ArrayList(picked)
                            },
                            label = member.displayName,
                            enabled = canEdit,
                        )
                    }
                }
                Text(
                    text = if (chore != null && chore.rotationMemberIds.toSet() != rotation.toSet() &&
                        chore.rotationMemberIds.isNotEmpty()
                    ) {
                        stringResource(R.string.chores_editor_turns_restart)
                    } else {
                        stringResource(R.string.chores_editor_turns_description)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (chore != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.chores_filter_paused),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(checked = !isActive, onCheckedChange = { isActive = !it }, enabled = canEdit)
                }
            }

            if (reminderEnabled != null && isActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = reminderEnabled, role = Role.Switch, onValueChange = onReminderChange),
                    horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.chores_editor_reminder_label),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.chores_editor_reminder_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = reminderEnabled, onCheckedChange = null)
                }
            }

            if (canEdit) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xSmall)) {
                    PrimaryButton(
                        text = if (chore == null) {
                            stringResource(R.string.chores_editor_add_action)
                        } else {
                            stringResource(R.string.common_save)
                        },
                        onClick = {
                            // Only removed members left means nobody rotates any more.
                            val rotationMemberIds = rotation.toList()
                                .takeIf { ids -> members.any { it.id in ids } }
                                .orEmpty()
                            onSave(name.trim(), category, schedule, isActive, rotationMemberIds)
                        },
                        enabled = name.isNotBlank() && scheduleComplete,
                    )
                    if (chore != null && canDelete && onDelete != null) {
                        TextButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.common_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else if (chore != null && !canDelete) {
                        Text(
                            text = stringResource(R.string.error_chore_delete_forbidden),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.common_close))
                }
            }
        }
    }
}

@Composable
private fun WeekdayChips(selected: List<Int>, onToggle: (Int) -> Unit, enabled: Boolean) {
    val locale = LocalConfiguration.current.locales[0]
    FlowRow(horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.small)) {
        weekdaysInOrder(locale).forEach { day ->
            ChoreChip(
                selected = day.isoDayNumber in selected,
                onClick = { onToggle(day.isoDayNumber) },
                label = day.shortName(locale).replaceFirstChar { it.titlecase(locale) },
                enabled = enabled,
            )
        }
    }
}
