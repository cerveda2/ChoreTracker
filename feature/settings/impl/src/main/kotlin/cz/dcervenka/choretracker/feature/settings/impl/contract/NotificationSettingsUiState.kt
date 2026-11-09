package cz.dcervenka.choretracker.feature.settings.impl.contract

import cz.dcervenka.choretracker.core.common.UiState

data class NotificationSettingsUiState(
    val remindersEnabled: Boolean = false,
    val hour: Int = 9,
    val minute: Int = 0,
    val inviteNotificationsEnabled: Boolean = true,
    // Invite-accepted pushes go to whoever created the invite, and only the owner and admins can.
    val canInvite: Boolean = true,
    val isLoading: Boolean = true,
) : UiState
