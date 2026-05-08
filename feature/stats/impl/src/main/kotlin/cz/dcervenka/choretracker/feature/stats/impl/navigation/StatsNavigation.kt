package cz.dcervenka.choretracker.feature.stats.impl.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import cz.dcervenka.choretracker.feature.stats.impl.screen.ChoreHistoryScreen
import cz.dcervenka.choretracker.feature.stats.impl.screen.StatsScreen
import cz.dcervenka.choretracker.feature.stats.impl.viewmodel.ChoreHistoryViewModel
import cz.dcervenka.choretracker.feature.stats.impl.viewmodel.StatsViewModel

fun NavGraphBuilder.statsScreen(
    navController: NavHostController,
) {
    composable(route = StatsDestination.route) {
        val viewModel: StatsViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        StatsScreen(
            uiState = uiState.value,
            onChoreClick = { choreId, choreName ->
                navController.navigate(StatsChoreHistoryDestination.createRoute(choreId, choreName))
            },
        )
    }

    composable(
        route = StatsChoreHistoryDestination.route,
        arguments = listOf(
            navArgument("choreId") { type = NavType.StringType },
            navArgument("choreName") { type = NavType.StringType },
        ),
    ) {
        val viewModel: ChoreHistoryViewModel = hiltViewModel()
        val completions = viewModel.completions.collectAsStateWithLifecycle()

        ChoreHistoryScreen(
            choreName = viewModel.choreName,
            completions = completions.value,
            onBack = { navController.popBackStack() },
        )
    }
}
