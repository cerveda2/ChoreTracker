package cz.dcervenka.choretracker.feature.onboarding.impl.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import cz.dcervenka.choretracker.feature.onboarding.impl.screen.OnboardingScreen
import cz.dcervenka.choretracker.feature.onboarding.impl.viewmodel.OnboardingViewModel

fun NavGraphBuilder.onboardingScreen() {
    composable(route = OnboardingDestination.route) {
        val viewModel: OnboardingViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        OnboardingScreen(
            uiState = uiState.value,
            onIntent = viewModel::dispatch,
        )
    }
}
