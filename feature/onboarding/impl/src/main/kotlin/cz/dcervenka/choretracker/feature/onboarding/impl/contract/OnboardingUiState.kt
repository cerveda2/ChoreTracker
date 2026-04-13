package cz.dcervenka.choretracker.feature.onboarding.impl.contract

import cz.dcervenka.choretracker.core.common.UiState

data class OnboardingUiState(
    val householdName: String = "",
    val displayName: String = "",
    val canEditDisplayName: Boolean = true,
    val inviteCode: String = "",
    val restoreErrorMessage: String? = null,
    val isRestoringRemoteHousehold: Boolean = false,
    val isWorking: Boolean = false,
    val errorMessage: String? = null,
) : UiState
