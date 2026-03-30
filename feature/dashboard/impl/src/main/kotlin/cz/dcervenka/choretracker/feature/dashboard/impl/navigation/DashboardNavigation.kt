package cz.dcervenka.choretracker.feature.dashboard.impl.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import cz.dcervenka.choretracker.feature.dashboard.impl.screen.DashboardScreen
import cz.dcervenka.choretracker.feature.dashboard.impl.viewmodel.DashboardViewModel

fun NavGraphBuilder.dashboardScreen() {
    composable(route = DashboardDestination.route) {
        val viewModel: DashboardViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        DashboardScreen(
            uiState = uiState.value,
            onLogCompletion = viewModel::logCompletion,
        )
    }
}
