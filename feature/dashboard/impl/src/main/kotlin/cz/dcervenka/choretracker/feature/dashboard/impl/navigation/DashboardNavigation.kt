package cz.dcervenka.choretracker.feature.dashboard.impl.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.DashboardUiIntent
import cz.dcervenka.choretracker.feature.dashboard.impl.screen.DashboardScreen
import cz.dcervenka.choretracker.feature.dashboard.impl.screen.RecentCompletionDetailScreen
import cz.dcervenka.choretracker.feature.dashboard.impl.screen.RecentCompletionsScreen
import cz.dcervenka.choretracker.feature.dashboard.impl.viewmodel.DashboardViewModel

fun NavGraphBuilder.dashboardScreen(
    navController: NavHostController,
) {
    composable(route = DashboardDestination.route) {
        val viewModel: DashboardViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        DashboardScreen(
            uiState = uiState.value,
            onIntent = viewModel::dispatch,
            undoEvents = viewModel.undoEvents,
            onSeeAllCompletions = {
                navController.navigate(DashboardCompletionsDestination.route)
            },
            onOpenCompletion = { completionId ->
                navController.navigate(DashboardCompletionDetailDestination.createRoute(completionId))
            },
        )
    }

    composable(route = DashboardCompletionsDestination.route) {
        val viewModel: DashboardViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        RecentCompletionsScreen(
            completions = uiState.value.allCompletions,
            onBack = { navController.popBackStack() },
            onOpenCompletion = { completionId ->
                navController.navigate(DashboardCompletionDetailDestination.createRoute(completionId))
            },
        )
    }

    composable(
        route = DashboardCompletionDetailDestination.route,
        arguments = listOf(navArgument("completionId") { type = NavType.StringType }),
    ) { backStackEntry ->
        val viewModel: DashboardViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()
        val completionId = backStackEntry.arguments?.getString("completionId").orEmpty()
        val completion = uiState.value.allCompletions.firstOrNull { it.completionId == completionId }

        RecentCompletionDetailScreen(
            completion = completion,
            onBack = { navController.popBackStack() },
            onDelete = {
                viewModel.dispatch(DashboardUiIntent.DeleteCompletion(completionId))
                navController.popBackStack()
            },
        )
    }
}
