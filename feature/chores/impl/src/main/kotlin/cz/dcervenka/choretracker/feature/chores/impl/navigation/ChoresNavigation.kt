package cz.dcervenka.choretracker.feature.chores.impl.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import cz.dcervenka.choretracker.feature.chores.impl.screen.ChoresScreen
import cz.dcervenka.choretracker.feature.chores.impl.viewmodel.ChoresViewModel

fun NavGraphBuilder.choresScreen() {
    composable(route = ChoresDestination.route) {
        val viewModel: ChoresViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        ChoresScreen(
            uiState = uiState.value,
            onIntent = viewModel::dispatch,
            undoEvents = viewModel.undoEvents,
            events = viewModel.events,
        )
    }
}
