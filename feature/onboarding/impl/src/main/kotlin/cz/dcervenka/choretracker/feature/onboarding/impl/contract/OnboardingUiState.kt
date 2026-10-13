package cz.dcervenka.choretracker.feature.onboarding.impl.contract

import cz.dcervenka.choretracker.core.common.UiState
import cz.dcervenka.choretracker.core.model.error.AppError

data class OnboardingUiState(
    val householdName: String = "",
    val displayName: String = "",
    val canEditDisplayName: Boolean = true,
    val inviteCode: String = "",
    val restoreError: AppError? = null,
    val isRestoringRemoteHousehold: Boolean = false,
    val isWorking: Boolean = false,
    val error: AppError? = null,
) : UiState
