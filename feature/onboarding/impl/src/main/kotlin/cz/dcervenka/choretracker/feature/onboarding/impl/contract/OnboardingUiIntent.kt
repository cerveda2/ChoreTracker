package cz.dcervenka.choretracker.feature.onboarding.impl.contract

import cz.dcervenka.choretracker.core.common.UiIntent

sealed interface OnboardingUiIntent : UiIntent {
    data class HouseholdNameChanged(val value: String) : OnboardingUiIntent
    data class DisplayNameChanged(val value: String) : OnboardingUiIntent
    data class InviteCodeChanged(val value: String) : OnboardingUiIntent
    data object CreateHousehold : OnboardingUiIntent
    data object JoinHousehold : OnboardingUiIntent
}
