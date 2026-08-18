package cz.dcervenka.choretracker.feature.settings.impl.contract

import cz.dcervenka.choretracker.core.common.UiState

data class NotificationSettingsUiState(
    val remindersEnabled: Boolean = false,
    val hour: Int = 9,
    val minute: Int = 0,
    val inviteNotificationsEnabled: Boolean = true,
    val isLoading: Boolean = true,
) : UiState
