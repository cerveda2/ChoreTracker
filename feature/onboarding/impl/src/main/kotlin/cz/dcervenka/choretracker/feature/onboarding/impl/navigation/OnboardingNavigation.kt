package cz.dcervenka.choretracker.feature.onboarding.impl.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import cz.dcervenka.choretracker.feature.onboarding.api.ONBOARDING_ROUTE
import cz.dcervenka.choretracker.feature.onboarding.impl.screen.OnboardingScreen
import cz.dcervenka.choretracker.feature.onboarding.impl.viewmodel.OnboardingViewModel

fun NavGraphBuilder.onboardingScreen() {
    composable(route = ONBOARDING_ROUTE) {
        val viewModel: OnboardingViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        OnboardingScreen(
            uiState = uiState.value,
            onHouseholdNameChange = viewModel::onHouseholdNameChange,
            onDisplayNameChange = viewModel::onDisplayNameChange,
            onInviteCodeChange = viewModel::onInviteCodeChange,
            onCreateHousehold = viewModel::createHousehold,
            onJoinHousehold = viewModel::joinHousehold,
        )
    }
}
