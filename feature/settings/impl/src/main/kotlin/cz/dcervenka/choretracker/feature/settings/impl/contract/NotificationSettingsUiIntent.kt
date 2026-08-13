package cz.dcervenka.choretracker.feature.settings.impl.contract

import cz.dcervenka.choretracker.core.common.UiIntent

sealed interface NotificationSettingsUiIntent : UiIntent {
    data class SetEnabled(val enabled: Boolean) : NotificationSettingsUiIntent
    data class SetTime(val hour: Int, val minute: Int) : NotificationSettingsUiIntent
}
