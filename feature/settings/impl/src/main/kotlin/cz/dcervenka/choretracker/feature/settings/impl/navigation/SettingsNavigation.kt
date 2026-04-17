package cz.dcervenka.choretracker.feature.settings.impl.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import cz.dcervenka.choretracker.feature.settings.impl.screen.AccountSettingsScreen
import cz.dcervenka.choretracker.feature.settings.impl.screen.ChoresSettingsScreen
import cz.dcervenka.choretracker.feature.settings.impl.screen.HouseholdSettingsScreen
import cz.dcervenka.choretracker.feature.settings.impl.screen.MembersSettingsScreen
import cz.dcervenka.choretracker.feature.settings.impl.screen.SettingsScreen
import cz.dcervenka.choretracker.feature.settings.impl.viewmodel.SettingsViewModel

fun NavGraphBuilder.settingsScreen(
    navController: NavHostController,
) {
    composable(route = SettingsDestination.route) {
        val viewModel: SettingsViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        SettingsScreen(
            uiState = uiState.value,
            onOpenHousehold = { navController.navigate(HouseholdSettingsDestination.route) },
            onOpenMembers = { navController.navigate(MembersSettingsDestination.route) },
            onOpenChores = { navController.navigate(ChoresSettingsDestination.route) },
            onOpenAccount = { navController.navigate(AccountSettingsDestination.route) },
        )
    }

    composable(route = HouseholdSettingsDestination.route) {
        val viewModel: SettingsViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        HouseholdSettingsScreen(
            uiState = uiState.value,
            onBack = { navController.popBackStack() },
            onIntent = viewModel::dispatch,
        )
    }

    composable(route = MembersSettingsDestination.route) {
        val viewModel: SettingsViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        MembersSettingsScreen(
            uiState = uiState.value,
            onBack = { navController.popBackStack() },
            onIntent = viewModel::dispatch,
        )
    }

    composable(route = ChoresSettingsDestination.route) {
        val viewModel: SettingsViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        ChoresSettingsScreen(
            uiState = uiState.value,
            onBack = { navController.popBackStack() },
            onIntent = viewModel::dispatch,
        )
    }

    composable(route = AccountSettingsDestination.route) {
        val viewModel: SettingsViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        AccountSettingsScreen(
            uiState = uiState.value,
            onBack = { navController.popBackStack() },
            onIntent = viewModel::dispatch,
        )
    }
}
