package cz.dcervenka.choretracker.feature.onboarding.impl.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import cz.dcervenka.choretracker.feature.onboarding.impl.screen.ManualCodeEntryScreen
import cz.dcervenka.choretracker.feature.onboarding.impl.screen.OnboardingScreen
import cz.dcervenka.choretracker.feature.onboarding.impl.screen.QrScanScreen
import cz.dcervenka.choretracker.feature.onboarding.impl.viewmodel.OnboardingViewModel

fun NavGraphBuilder.onboardingScreen(navController: NavHostController) {
    navigation(startDestination = OnboardingDestination.route, route = OnboardingGraphDestination.route) {
        composable(route = OnboardingDestination.route) { backStackEntry ->
            val viewModel = sharedOnboardingViewModel(navController, backStackEntry)
            val uiState = viewModel.uiState.collectAsStateWithLifecycle()

            OnboardingScreen(
                uiState = uiState.value,
                onIntent = viewModel::dispatch,
                onJoinHousehold = { navController.navigate(QrScanDestination.route) },
            )
        }

        composable(route = QrScanDestination.route) { backStackEntry ->
            val viewModel = sharedOnboardingViewModel(navController, backStackEntry)
            val uiState = viewModel.uiState.collectAsStateWithLifecycle()

            QrScanScreen(
                uiState = uiState.value,
                onIntent = viewModel::dispatch,
                onEnterManually = { navController.navigate(ManualCodeEntryDestination.route) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = ManualCodeEntryDestination.route) { backStackEntry ->
            val viewModel = sharedOnboardingViewModel(navController, backStackEntry)
            val uiState = viewModel.uiState.collectAsStateWithLifecycle()

            ManualCodeEntryScreen(
                uiState = uiState.value,
                onIntent = viewModel::dispatch,
                onScanQr = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
    }
}

// Scoping to backStackEntry (the individual screen) instead of the graph is what caused each of
// the three onboarding screens to get its own OnboardingViewModel - and with it, its own
// household-name/display-name/invite-code draft and error state, silently dropped on every
// step forward or back. Scoping to the graph's own entry gives all three screens the same
// instance, matching the fact that they're really one continuous flow.
@Composable
private fun sharedOnboardingViewModel(
    navController: NavHostController,
    backStackEntry: NavBackStackEntry,
): OnboardingViewModel {
    val parentEntry = remember(backStackEntry) {
        navController.getBackStackEntry(OnboardingGraphDestination.route)
    }
    return hiltViewModel(parentEntry)
}
