package cz.dcervenka.choretracker.feature.settings.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.ChoreTopAppBar
import cz.dcervenka.choretracker.core.design.components.LoadingState
import cz.dcervenka.choretracker.core.design.components.SettingsListItem
import cz.dcervenka.choretracker.feature.settings.impl.contract.NotificationSettingsUiIntent
import cz.dcervenka.choretracker.feature.settings.impl.contract.NotificationSettingsUiState

@Composable
fun NotificationSettingsScreen(
    uiState: NotificationSettingsUiState,
    onBack: () -> Unit,
    onIntent: (NotificationSettingsUiIntent) -> Unit,
) {
    if (uiState.isLoading) {
        LoadingState(message = stringResource(R.string.settings_notifications_loading))
        return
    }

    val spacing = LocalSpacing.current
    var showTimePicker by remember { mutableStateOf(false) }

    ChoreScaffold(
        topBar = {
            ChoreTopAppBar(
                title = stringResource(R.string.settings_notifications_title),
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
                SettingsGroup(title = stringResource(R.string.settings_notifications_reminders_section)) {
                    SettingsListItem(
                        title = stringResource(R.string.settings_notifications_enabled_label),
                        subtitle = stringResource(R.string.settings_notifications_enabled_description),
                        trailingContent = {
                            Switch(
                                checked = uiState.remindersEnabled,
                                onCheckedChange = {
                                    onIntent(NotificationSettingsUiIntent.SetRemindersEnabled(it))
                                },
                            )
                        },
                    )
                    if (uiState.remindersEnabled) {
                        HorizontalDivider()
                        SettingsListItem(
                            title = stringResource(R.string.settings_notifications_time_label),
                            subtitle = "%02d:%02d".format(uiState.hour, uiState.minute),
                            onClick = { showTimePicker = true },
                        )
                    }
                }
            }
            item {
                SettingsGroup(title = stringResource(R.string.settings_notifications_invite_section)) {
                    SettingsListItem(
                        title = stringResource(R.string.settings_notifications_invite_enabled_label),
                        subtitle = stringResource(R.string.settings_notifications_invite_enabled_description),
                        trailingContent = {
                            Switch(
                                checked = uiState.inviteNotificationsEnabled,
                                onCheckedChange = {
                                    onIntent(NotificationSettingsUiIntent.SetInviteNotificationsEnabled(it))
                                },
                            )
                        },
                    )
                }
            }
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initialHour = uiState.hour,
            initialMinute = uiState.minute,
            onDismissRequest = { showTimePicker = false },
            onConfirm = { hour, minute ->
                onIntent(NotificationSettingsUiIntent.SetTime(hour, minute))
                showTimePicker = false
            },
        )
    }
}
