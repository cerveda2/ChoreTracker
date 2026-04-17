package cz.dcervenka.choretracker.feature.household.impl.contract

import cz.dcervenka.choretracker.core.common.UiIntent

sealed interface HouseholdUiIntent : UiIntent {
    data object RefreshInvite : HouseholdUiIntent
}
