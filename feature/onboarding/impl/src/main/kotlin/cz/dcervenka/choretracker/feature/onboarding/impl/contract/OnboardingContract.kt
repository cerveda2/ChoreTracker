package cz.dcervenka.choretracker.feature.onboarding.impl.contract

import cz.dcervenka.choretracker.core.common.UiState

data class OnboardingUiState(
    val householdName: String = "",
    val displayName: String = "",
    val inviteCode: String = "",
    val isWorking: Boolean = false,
    val errorMessage: String? = null,
) : UiState
