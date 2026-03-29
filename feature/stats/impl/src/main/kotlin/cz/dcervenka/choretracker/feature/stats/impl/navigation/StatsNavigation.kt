package cz.dcervenka.choretracker.feature.stats.impl.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import cz.dcervenka.choretracker.feature.stats.api.STATS_ROUTE
import cz.dcervenka.choretracker.feature.stats.impl.screen.StatsScreen
import cz.dcervenka.choretracker.feature.stats.impl.viewmodel.StatsViewModel

fun NavGraphBuilder.statsScreen() {
    composable(route = STATS_ROUTE) {
        val viewModel: StatsViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        StatsScreen(uiState = uiState.value)
    }
}
