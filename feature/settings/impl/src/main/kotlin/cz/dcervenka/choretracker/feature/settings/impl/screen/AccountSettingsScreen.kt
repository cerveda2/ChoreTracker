package cz.dcervenka.choretracker.feature.settings.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.ChoreTopAppBar
import cz.dcervenka.choretracker.core.design.components.ListGroup
import cz.dcervenka.choretracker.core.design.components.PrimaryButton
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
        }
    }
}
