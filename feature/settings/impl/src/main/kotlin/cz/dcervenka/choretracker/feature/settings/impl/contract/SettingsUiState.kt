package cz.dcervenka.choretracker.feature.settings.impl.contract

import cz.dcervenka.choretracker.core.common.UiState
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
import cz.dcervenka.choretracker.core.model.household.Household
import cz.dcervenka.choretracker.core.model.household.HouseholdMember

data class SettingsUiState(
    val userLabel: String? = null,
    val userEmail: String? = null,
    val household: Household? = null,
    val members: List<HouseholdMember> = emptyList(),
    val chores: List<Chore> = emptyList(),
    val accountDisplayNameInput: String = "",
    val householdNameInput: String = "",
    val memberInput: String = "",
    val choreInput: String = "",
    val choreCategoryInput: ChoreCategory = ChoreCategory.OTHER,
    val requiresConfiguration: Boolean = false,
    val isSignedOut: Boolean = false,
) : UiState
