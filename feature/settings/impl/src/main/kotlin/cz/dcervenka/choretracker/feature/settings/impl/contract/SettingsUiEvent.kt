package cz.dcervenka.choretracker.feature.settings.impl.contract

sealed interface SettingsUiEvent {
    data object ChoreAdded : SettingsUiEvent
    data object ChoreDeleted : SettingsUiEvent
    data object ChoreSaved : SettingsUiEvent
    data object MemberAdded : SettingsUiEvent
    data object NameSaved : SettingsUiEvent
    data class Error(val message: String) : SettingsUiEvent
}
