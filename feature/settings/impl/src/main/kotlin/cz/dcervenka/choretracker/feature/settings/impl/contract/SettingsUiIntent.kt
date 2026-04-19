package cz.dcervenka.choretracker.feature.settings.impl.contract

import cz.dcervenka.choretracker.core.common.UiIntent
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory

sealed interface SettingsUiIntent : UiIntent {
    data class AccountDisplayNameChanged(val value: String) : SettingsUiIntent
    data class HouseholdNameChanged(val value: String) : SettingsUiIntent
    data class MemberInputChanged(val value: String) : SettingsUiIntent
    data class ChoreInputChanged(val value: String) : SettingsUiIntent
    data class ChoreCategoryInputChanged(val category: ChoreCategory) : SettingsUiIntent
    data object SaveAccountDisplayName : SettingsUiIntent
    data object SignOut : SettingsUiIntent
    data object SaveHouseholdName : SettingsUiIntent
    data object AddMember : SettingsUiIntent
    data object AddChore : SettingsUiIntent
    data object RefreshInvite : SettingsUiIntent
    data class UpdateChoreActive(val choreId: String, val isActive: Boolean) : SettingsUiIntent
    data class DeleteChore(val choreId: String) : SettingsUiIntent
    data class UpdateChoreFrequency(val choreId: String, val frequencyDays: Int?) : SettingsUiIntent
    data class UpdateChoreName(val choreId: String, val name: String) : SettingsUiIntent
    data class UpdateChoreCategory(val choreId: String, val category: ChoreCategory) : SettingsUiIntent
}
