package cz.dcervenka.choretracker.feature.onboarding.impl.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import cz.dcervenka.choretracker.feature.onboarding.impl.screen.ManualCodeEntryScreen
import cz.dcervenka.choretracker.feature.onboarding.impl.screen.OnboardingScreen
import cz.dcervenka.choretracker.feature.onboarding.impl.screen.QrScanScreen
import cz.dcervenka.choretracker.feature.onboarding.impl.viewmodel.OnboardingViewModel

fun NavGraphBuilder.onboardingScreen(navController: NavHostController) {
    composable(route = OnboardingDestination.route) {
        val viewModel: OnboardingViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        OnboardingScreen(
            uiState = uiState.value,
            onIntent = viewModel::dispatch,
            onJoinHousehold = { navController.navigate(QrScanDestination.route) },
        )
    }

    composable(route = QrScanDestination.route) {
        val viewModel: OnboardingViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        QrScanScreen(
            uiState = uiState.value,
            onIntent = viewModel::dispatch,
            onEnterManually = { navController.navigate(ManualCodeEntryDestination.route) },
            onBack = { navController.popBackStack() },
        )
    }

    composable(route = ManualCodeEntryDestination.route) {
        val viewModel: OnboardingViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        ManualCodeEntryScreen(
            uiState = uiState.value,
            onIntent = viewModel::dispatch,
            onScanQr = { navController.popBackStack() },
            onBack = { navController.popBackStack() },
        )
    }
}
