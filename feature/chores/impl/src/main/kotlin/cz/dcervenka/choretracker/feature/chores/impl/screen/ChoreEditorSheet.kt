package cz.dcervenka.choretracker.feature.chores.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreChip
import cz.dcervenka.choretracker.core.design.components.PrimaryButton
import cz.dcervenka.choretracker.core.design.suggestions
import cz.dcervenka.choretracker.core.design.toIcon
import cz.dcervenka.choretracker.core.design.toStringRes
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory

private val PRESET_FREQUENCY_DAYS = listOf(1, 2, 3, 7)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChoreEditorSheet(
    chore: Chore?,
    isOwner: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, category: ChoreCategory, frequencyDays: Int?, isActive: Boolean) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by rememberSaveable(chore?.id) { mutableStateOf(chore?.name.orEmpty()) }
    var category by rememberSaveable(chore?.id) { mutableStateOf(chore?.category ?: ChoreCategory.OTHER) }
    var presetFrequencyDays by rememberSaveable(chore?.id) {
        mutableStateOf(chore?.frequencyDays?.takeIf { it in PRESET_FREQUENCY_DAYS })
    }
    var customSelected by rememberSaveable(chore?.id) {
        mutableStateOf(chore?.frequencyDays != null && chore.frequencyDays !in PRESET_FREQUENCY_DAYS)
    }
    var customFrequencyInput by rememberSaveable(chore?.id) {
        mutableStateOf(chore?.frequencyDays?.takeIf { it !in PRESET_FREQUENCY_DAYS }?.toString().orEmpty())
    }
    var isActive by rememberSaveable(chore?.id) { mutableStateOf(chore?.isActive ?: true) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    val frequencyDays = if (customSelected) customFrequencyInput.toIntOrNull()?.takeIf { it > 0 } else presetFrequencyDays

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
                .padding(horizontal = spacing.large)
                .padding(bottom = spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Text(
                text = chore?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chores_new_chore),
                style = MaterialTheme.typography.headlineSmall,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.chores_editor_name_hint)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    autoCorrectEnabled = true,
                ),
                enabled = isOwner,
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
                        enabled = isOwner,
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
                                enabled = isOwner,
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
                customSelected = false
                presetFrequencyDays = days
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                ChoreChip(
                    selected = !customSelected && presetFrequencyDays == null,
                    onClick = { selectPreset(null) },
                    label = stringResource(R.string.chores_editor_frequency_none),
                    enabled = isOwner,
                )
                ChoreChip(
                    selected = !customSelected && presetFrequencyDays == 1,
                    onClick = { selectPreset(1) },
                    label = stringResource(R.string.chores_editor_frequency_daily),
                    enabled = isOwner,
                )
                ChoreChip(
                    selected = !customSelected && presetFrequencyDays == 2,
                    onClick = { selectPreset(2) },
                    label = pluralStringResource(R.plurals.log_sheet_meta_frequency, 2, 2),
                    enabled = isOwner,
                )
                ChoreChip(
                    selected = !customSelected && presetFrequencyDays == 3,
                    onClick = { selectPreset(3) },
                    label = pluralStringResource(R.plurals.log_sheet_meta_frequency, 3, 3),
                    enabled = isOwner,
                )
                ChoreChip(
                    selected = !customSelected && presetFrequencyDays == 7,
                    onClick = { selectPreset(7) },
                    label = stringResource(R.string.chores_editor_frequency_weekly),
                    enabled = isOwner,
                )
                ChoreChip(
                    selected = customSelected,
                    onClick = { customSelected = true },
                    label = stringResource(R.string.chores_editor_frequency_custom),
                    enabled = isOwner,
                )
            }
            if (customSelected) {
                OutlinedTextField(
                    value = customFrequencyInput,
                    onValueChange = { customFrequencyInput = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.chores_editor_frequency_custom_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = isOwner,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
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
                    Switch(checked = !isActive, onCheckedChange = { isActive = !it }, enabled = isOwner)
                }
            }

            if (isOwner) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xSmall)) {
                    PrimaryButton(
                        text = if (chore == null) {
                            stringResource(R.string.chores_editor_add_action)
                        } else {
                            stringResource(R.string.common_save)
                        },
                        onClick = { onSave(name.trim(), category, frequencyDays, isActive) },
                        enabled = name.isNotBlank() && (!customSelected || frequencyDays != null),
                    )
                    if (chore != null && onDelete != null) {
                        TextButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.common_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
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
