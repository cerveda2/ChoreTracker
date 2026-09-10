package cz.dcervenka.choretracker.feature.settings.impl.contract

import cz.dcervenka.choretracker.core.common.UiIntent
import cz.dcervenka.choretracker.core.model.settings.ThemeMode

sealed interface SettingsUiIntent : UiIntent {
    data class AccountDisplayNameChanged(val value: String) : SettingsUiIntent
    data class HouseholdNameChanged(val value: String) : SettingsUiIntent
    data class MemberInputChanged(val value: String) : SettingsUiIntent
    data object SaveAccountDisplayName : SettingsUiIntent
    data object SignOut : SettingsUiIntent
    data object SaveHouseholdName : SettingsUiIntent
    data object AddMember : SettingsUiIntent
    data object RefreshInvite : SettingsUiIntent
    data class RemoveMember(val memberId: String) : SettingsUiIntent
    data class GenerateMemberInvite(val memberId: String) : SettingsUiIntent
    data class TransferOwnership(val newOwnerMemberId: String) : SettingsUiIntent
    data object LeaveHousehold : SettingsUiIntent
    data class SetThemeMode(val mode: ThemeMode) : SettingsUiIntent
    data class SetDynamicColor(val enabled: Boolean) : SettingsUiIntent
}
