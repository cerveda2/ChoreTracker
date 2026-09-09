package cz.dcervenka.choretracker.feature.settings.impl.contract

import cz.dcervenka.choretracker.core.common.UiState
import cz.dcervenka.choretracker.core.model.household.Household
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import cz.dcervenka.choretracker.core.model.household.Invite
import cz.dcervenka.choretracker.core.model.settings.ReminderSettings
import cz.dcervenka.choretracker.core.model.settings.ThemeSettings

data class SettingsUiState(
    val userLabel: String? = null,
    val userEmail: String? = null,
    val household: Household? = null,
    val members: List<HouseholdMember> = emptyList(),
    val invites: List<Invite> = emptyList(),
    val accountDisplayNameInput: String = "",
    val householdNameInput: String = "",
    val memberInput: String = "",
    val requiresConfiguration: Boolean = false,
    val isSignedOut: Boolean = false,
    val themeSettings: ThemeSettings = ThemeSettings(),
    val reminderSettings: ReminderSettings = ReminderSettings(),
) : UiState {
    val isOwner: Boolean
        get() = members.any { it.isCurrentUser && it.role == HouseholdRole.OWNER }
}
