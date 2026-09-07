package cz.dcervenka.choretracker.feature.dashboard.impl.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import cz.dcervenka.choretracker.feature.dashboard.impl.contract.DashboardUiIntent
import cz.dcervenka.choretracker.feature.dashboard.impl.screen.DashboardScreen
import cz.dcervenka.choretracker.feature.dashboard.impl.screen.LogChoreScreen
import cz.dcervenka.choretracker.feature.dashboard.impl.screen.RecentCompletionDetailScreen
import cz.dcervenka.choretracker.feature.dashboard.impl.screen.RecentCompletionsScreen
import cz.dcervenka.choretracker.feature.dashboard.impl.viewmodel.DashboardViewModel

fun NavGraphBuilder.dashboardScreen(
    navController: NavHostController,
    onOpenSettings: () -> Unit,
) {
    composable(route = DashboardDestination.route) {
        val viewModel: DashboardViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        DashboardScreen(
            uiState = uiState.value,
            onIntent = viewModel::dispatch,
            undoEvents = viewModel.undoEvents,
            errorEvents = viewModel.errorEvents,
            onLogChore = { navController.navigate(DashboardLogChoreDestination.route) },
            onSeeAllCompletions = {
                navController.navigate(DashboardCompletionsDestination.route)
            },
            onOpenCompletion = { completionId ->
                navController.navigate(DashboardCompletionDetailDestination.createRoute(completionId))
            },
            onOpenSettings = onOpenSettings,
        )
    }

    composable(route = DashboardLogChoreDestination.route) {
        val viewModel: DashboardViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        LogChoreScreen(
            uiState = uiState.value,
            onIntent = viewModel::dispatch,
            undoEvents = viewModel.undoEvents,
            errorEvents = viewModel.errorEvents,
            onBack = { navController.popBackStack() },
        )
    }

    composable(route = DashboardCompletionsDestination.route) {
        val viewModel: DashboardViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()
        val completionHistory = viewModel.completionHistory.collectAsStateWithLifecycle()

        RecentCompletionsScreen(
            completions = completionHistory.value.orEmpty(),
            members = uiState.value.members,
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
        val completionHistory = viewModel.completionHistory.collectAsStateWithLifecycle()
        val completionId = backStackEntry.arguments?.getString("completionId").orEmpty()
        val history = completionHistory.value
        val completion = history?.firstOrNull { it.completionId == completionId }

        LaunchedEffect(history, completion) {
            if (history != null && completion == null) {
                navController.popBackStack()
            }
        }

        RecentCompletionDetailScreen(
            completion = completion,
            uiState = uiState.value,
            errorEvents = viewModel.errorEvents,
            onBack = { navController.popBackStack() },
            onDelete = {
                // Deliberately doesn't pop back here - the LaunchedEffect above already does that
                // reactively once `completion` actually disappears from uiState, which only
                // happens on a successful delete. Popping back unconditionally here would leave
                // no screen around to show the error snackbar if the delete is rejected.
                viewModel.dispatch(DashboardUiIntent.DeleteCompletion(completionId))
            },
            onUpdate = { note, participantIds ->
                viewModel.dispatch(DashboardUiIntent.UpdateCompletion(completionId, note, participantIds))
            },
        )
    }
}
