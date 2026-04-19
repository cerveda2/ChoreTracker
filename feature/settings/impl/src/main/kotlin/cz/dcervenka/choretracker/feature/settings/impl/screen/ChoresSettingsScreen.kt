package cz.dcervenka.choretracker.feature.settings.impl.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.ChoreTopAppBar
import cz.dcervenka.choretracker.core.design.components.EmptyState
import cz.dcervenka.choretracker.core.design.components.PrimaryButton
import cz.dcervenka.choretracker.core.design.components.ScreenHeader
import cz.dcervenka.choretracker.core.design.components.SectionCard
import cz.dcervenka.choretracker.core.design.toIcon
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiIntent
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiState

@Composable
fun ChoresSettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onIntent: (SettingsUiIntent) -> Unit,
) {
    val spacing = LocalSpacing.current
    var pendingDeleteChoreId by remember { mutableStateOf<String?>(null) }
    val pendingDeleteChore = uiState.chores.firstOrNull { it.id == pendingDeleteChoreId }
    var pendingFrequencyChoreId by remember { mutableStateOf<String?>(null) }
    val pendingFrequencyChore = uiState.chores.firstOrNull { it.id == pendingFrequencyChoreId }
    var pendingRenameChoreId by remember { mutableStateOf<String?>(null) }
    val pendingRenameChore = uiState.chores.firstOrNull { it.id == pendingRenameChoreId }
    var pendingCategoryChoreId by remember { mutableStateOf<String?>(null) }
    val pendingCategoryChore = uiState.chores.firstOrNull { it.id == pendingCategoryChoreId }

    ChoreScaffold(
        topBar = {
            ChoreTopAppBar(
                title = stringResource(R.string.settings_manage_chores_title),
                onBackClick = onBack,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = detailContentPadding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            item {
                ScreenHeader(
                    title = stringResource(R.string.household_chores),
                    subtitle = stringResource(R.string.household_chore_count, uiState.chores.size),
                )
            }
            item {
                SectionCard(title = stringResource(R.string.household_chores)) {
                    if (uiState.chores.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.settings_chores_empty_title),
                            message = stringResource(R.string.settings_chores_empty_message),
                        )
                    } else {
                        uiState.chores.forEach { chore ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                IconButton(onClick = { pendingCategoryChoreId = chore.id }) {
                                    Icon(
                                        imageVector = chore.category.toIcon(),
                                        contentDescription = stringResource(R.string.settings_chore_category_title),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = chore.name)
                                    chore.frequencyDays?.let { days ->
                                        Text(
                                            text = stringResource(R.string.settings_chore_frequency_every_n_days, days),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { pendingRenameChoreId = chore.id }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = stringResource(
                                                R.string.settings_chore_rename_title,
                                            ),
                                        )
                                    }
                                    IconButton(onClick = { pendingFrequencyChoreId = chore.id }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Schedule,
                                            contentDescription = stringResource(
                                                R.string.settings_chore_set_frequency_title,
                                            ),
                                        )
                                    }
                                    Switch(
                                        checked = chore.isActive,
                                        onCheckedChange = { checked ->
                                            onIntent(
                                                SettingsUiIntent.UpdateChoreActive(
                                                    choreId = chore.id,
                                                    isActive = checked,
                                                ),
                                            )
                                        },
                                    )
                                    IconButton(onClick = { pendingDeleteChoreId = chore.id }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = stringResource(R.string.household_delete_chore),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = uiState.choreInput,
                        onValueChange = { onIntent(SettingsUiIntent.ChoreInputChanged(it)) },
                        label = { Text(text = stringResource(R.string.household_new_chore)) },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            autoCorrectEnabled = true,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(spacing.xSmall),
                    ) {
                        ChoreCategory.entries.forEach { category ->
                            FilterChip(
                                selected = uiState.choreCategoryInput == category,
                                onClick = { onIntent(SettingsUiIntent.ChoreCategoryInputChanged(category)) },
                                label = { Text(category.label()) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = category.toIcon(),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                    }
                    PrimaryButton(
                        text = stringResource(R.string.household_add_chore),
                        onClick = { onIntent(SettingsUiIntent.AddChore) },
                    )
                }
            }
        }
    }

    if (pendingDeleteChore != null) {
        DeleteChoreDialog(
            onDismiss = { pendingDeleteChoreId = null },
            onDelete = {
                onIntent(SettingsUiIntent.DeleteChore(pendingDeleteChore.id))
                pendingDeleteChoreId = null
            },
        )
    }

    if (pendingRenameChore != null) {
        RenameChoreDialog(
            choreId = pendingRenameChore.id,
            initialName = pendingRenameChore.name,
            onDismiss = { pendingRenameChoreId = null },
            onSave = { name ->
                onIntent(SettingsUiIntent.UpdateChoreName(pendingRenameChore.id, name))
                pendingRenameChoreId = null
            },
        )
    }

    if (pendingFrequencyChore != null) {
        ChoreFrequencyDialog(
            choreId = pendingFrequencyChore.id,
            initialFrequencyDays = pendingFrequencyChore.frequencyDays,
            onDismiss = { pendingFrequencyChoreId = null },
            onSave = { days ->
                onIntent(SettingsUiIntent.UpdateChoreFrequency(pendingFrequencyChore.id, days))
                pendingFrequencyChoreId = null
            },
            onClear = {
                onIntent(SettingsUiIntent.UpdateChoreFrequency(pendingFrequencyChore.id, null))
                pendingFrequencyChoreId = null
            },
        )
    }

    if (pendingCategoryChore != null) {
        ChoreCategoryDialog(
            choreId = pendingCategoryChore.id,
            currentCategory = pendingCategoryChore.category,
            onDismiss = { pendingCategoryChoreId = null },
            onSave = { category ->
                onIntent(SettingsUiIntent.UpdateChoreCategory(pendingCategoryChore.id, category))
                pendingCategoryChoreId = null
            },
        )
    }
}

@Composable
private fun ChoreCategory.label(): String = when (this) {
    ChoreCategory.CLEANING -> stringResource(R.string.chore_category_cleaning)
    ChoreCategory.COOKING -> stringResource(R.string.chore_category_cooking)
    ChoreCategory.SHOPPING -> stringResource(R.string.chore_category_shopping)
    ChoreCategory.OUTDOOR -> stringResource(R.string.chore_category_outdoor)
    ChoreCategory.OTHER -> stringResource(R.string.chore_category_other)
}

@Composable
private fun DeleteChoreDialog(
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.household_delete_chore_title)) },
        text = { Text(stringResource(R.string.household_delete_chore_message)) },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.common_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun RenameChoreDialog(
    choreId: String,
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var nameInput by remember(choreId) { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_chore_rename_title)) },
        text = {
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text(stringResource(R.string.settings_chore_rename_hint)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    autoCorrectEnabled = true,
                ),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = nameInput.trim()
                    if (trimmed.isNotEmpty()) {
                        onSave(trimmed)
                    } else {
                        onDismiss()
                    }
                },
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun ChoreFrequencyDialog(
    choreId: String,
    initialFrequencyDays: Int?,
    onDismiss: () -> Unit,
    onSave: (Int?) -> Unit,
    onClear: () -> Unit,
) {
    var frequencyInput by remember(choreId) {
        mutableStateOf(initialFrequencyDays?.toString().orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_chore_set_frequency_title)) },
        text = {
            OutlinedTextField(
                value = frequencyInput,
                onValueChange = { frequencyInput = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.settings_chore_set_frequency_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(frequencyInput.toIntOrNull()?.takeIf { it > 0 })
                },
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.settings_chore_clear_frequency))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        },
    )
}

@Composable
private fun ChoreCategoryDialog(
    choreId: String,
    currentCategory: ChoreCategory,
    onDismiss: () -> Unit,
    onSave: (ChoreCategory) -> Unit,
) {
    var selected by remember(choreId) { mutableStateOf(currentCategory) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_chore_category_title)) },
        text = {
            Column {
                ChoreCategory.entries.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = category.toIcon(),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = category.label(),
                            modifier = Modifier.weight(1f),
                        )
                        RadioButton(
                            selected = selected == category,
                            onClick = { selected = category },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected) }) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
