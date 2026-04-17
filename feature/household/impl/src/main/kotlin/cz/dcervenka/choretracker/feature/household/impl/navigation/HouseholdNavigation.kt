package cz.dcervenka.choretracker.feature.household.impl.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import cz.dcervenka.choretracker.feature.household.impl.screen.HouseholdScreen
import cz.dcervenka.choretracker.feature.household.impl.viewmodel.HouseholdViewModel

fun NavGraphBuilder.householdScreen() {
    composable(route = HouseholdDestination.route) {
        val viewModel: HouseholdViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        HouseholdScreen(
            uiState = uiState.value,
            onIntent = viewModel::dispatch,
        )
    }
}
