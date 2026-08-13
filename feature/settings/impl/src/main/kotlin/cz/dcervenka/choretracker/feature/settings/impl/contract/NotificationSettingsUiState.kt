package cz.dcervenka.choretracker.feature.settings.impl.contract

import cz.dcervenka.choretracker.core.common.UiState

data class NotificationSettingsUiState(
    val enabled: Boolean = true,
    val hour: Int = 9,
    val minute: Int = 0,
    val isLoading: Boolean = true,
) : UiState
