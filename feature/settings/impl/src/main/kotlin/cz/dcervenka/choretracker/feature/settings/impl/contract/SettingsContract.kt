package cz.dcervenka.choretracker.feature.settings.impl.contract

import cz.dcervenka.choretracker.core.common.UiState

data class SettingsUiState(
    val userLabel: String? = null,
    val requiresConfiguration: Boolean = false,
    val isSignedOut: Boolean = false,
) : UiState
