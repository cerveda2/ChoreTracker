package cz.dcervenka.choretracker.feature.settings.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.ChoreTopAppBar
import cz.dcervenka.choretracker.core.design.components.ListGroup
import cz.dcervenka.choretracker.core.design.components.PrimaryButton
import cz.dcervenka.choretracker.core.design.components.SectionHeader
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiEvent
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiIntent
import cz.dcervenka.choretracker.feature.settings.impl.contract.SettingsUiState
import kotlinx.coroutines.flow.Flow

@Composable
fun AccountSettingsScreen(
    uiState: SettingsUiState,
    events: Flow<SettingsUiEvent>,
    onBack: () -> Unit,
    onIntent: (SettingsUiIntent) -> Unit,
) {
    val spacing = LocalSpacing.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val msgNameSaved = stringResource(R.string.settings_feedback_name_saved)
    val msgError = stringResource(R.string.settings_feedback_error)
    LaunchedEffect(events) {
        events.collect { event ->
            val msg = when (event) {
                SettingsUiEvent.NameSaved -> msgNameSaved
                is SettingsUiEvent.Error -> event.message.ifBlank { msgError }
                else -> return@collect
            }
            snackbarHostState.showSnackbar(msg)
        }
    }
    val emailValue = uiState.userEmail.orEmpty()
    val canSaveDisplayName = uiState.accountDisplayNameInput.trim().isNotBlank() &&
        uiState.accountDisplayNameInput.trim() != uiState.userLabel
    var showLeaveConfirm by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text(stringResource(R.string.settings_leave_household_title)) },
            text = { Text(stringResource(R.string.settings_leave_household_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveConfirm = false
                    onIntent(SettingsUiIntent.LeaveHousehold)
                }) {
                    Text(
                        text = stringResource(R.string.settings_leave_household),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.settings_delete_account_title)) },
            text = { Text(stringResource(R.string.settings_delete_account_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onIntent(SettingsUiIntent.DeleteAccount)
                }) {
                    Text(
                        text = stringResource(R.string.settings_delete_account_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    ChoreScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            ChoreTopAppBar(
                title = stringResource(R.string.settings_account_title),
                onBackClick = onBack,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = detailContentPadding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(spacing.large),
        ) {
            item {
                ListGroup {
                    Column(
                        modifier = Modifier.padding(spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(spacing.medium),
                    ) {
                        OutlinedTextField(
                            value = uiState.accountDisplayNameInput,
                            onValueChange = { onIntent(SettingsUiIntent.AccountDisplayNameChanged(it)) },
                            label = { Text(stringResource(R.string.settings_account_display_name_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = emailValue,
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text(stringResource(R.string.settings_account_email_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                        Text(
                            text = stringResource(R.string.settings_account_email_supporting),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PrimaryButton(
                            text = stringResource(R.string.settings_save_display_name),
                            onClick = {
                                keyboardController?.hide()
                                onIntent(SettingsUiIntent.SaveAccountDisplayName)
                            },
                            enabled = canSaveDisplayName,
                        )
                    }
                }
            }
            if (uiState.household != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                        SectionHeader(title = stringResource(R.string.settings_membership_section))
                        ListGroup {
                            TextButton(
                                onClick = { showLeaveConfirm = true },
                                enabled = uiState.canLeaveHousehold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(spacing.small),
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_leave_household),
                                    color = if (uiState.canLeaveHousehold) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        if (uiState.isOwner) {
                            Text(
                                text = stringResource(
                                    if (uiState.eligibleTransferTargets.isEmpty()) {
                                        R.string.settings_leave_household_sole_owner_hint
                                    } else {
                                        R.string.settings_leave_household_owner_hint
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = spacing.small),
                            )
                        }
                    }
                }
            }
            if (uiState.userLabel != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                        SectionHeader(title = stringResource(R.string.settings_danger_zone_section))
                        ListGroup {
                            TextButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(spacing.small),
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_delete_account),
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
