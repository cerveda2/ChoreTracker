package cz.dcervenka.choretracker.feature.settings.impl.contract

sealed interface SettingsUiEvent {
    data object MemberAdded : SettingsUiEvent
    data object MemberRemoved : SettingsUiEvent
    data object OwnershipTransferred : SettingsUiEvent
    data object NameSaved : SettingsUiEvent
    data class Error(val message: String) : SettingsUiEvent
    data class MemberInviteGenerated(val code: String) : SettingsUiEvent
}
