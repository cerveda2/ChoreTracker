package cz.dcervenka.choretracker.feature.settings.impl.contract

import cz.dcervenka.choretracker.core.model.error.AppError

sealed interface SettingsUiEvent {
    data object MemberAdded : SettingsUiEvent
    data object MemberRemoved : SettingsUiEvent
    data object OwnershipTransferred : SettingsUiEvent
    data object NameSaved : SettingsUiEvent
    data class Error(val error: AppError) : SettingsUiEvent
    data class MemberInviteGenerated(val code: String) : SettingsUiEvent
}
