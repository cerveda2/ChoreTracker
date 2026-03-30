package cz.dcervenka.choretracker.feature.settings.impl.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import cz.dcervenka.choretracker.feature.settings.impl.screen.SettingsScreen
import cz.dcervenka.choretracker.feature.settings.impl.viewmodel.SettingsViewModel

fun NavGraphBuilder.settingsScreen() {
    composable(route = SettingsDestination.route) {
        val viewModel: SettingsViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        SettingsScreen(
            uiState = uiState.value,
            onHouseholdNameChange = viewModel::onHouseholdNameChange,
            onSaveHouseholdName = viewModel::saveHouseholdName,
            onMemberInputChange = viewModel::onMemberInputChange,
            onChoreInputChange = viewModel::onChoreInputChange,
            onAddMember = viewModel::addMember,
            onAddChore = viewModel::addChore,
            onRefreshInvite = viewModel::refreshInvite,
            onUpdateChoreActive = viewModel::updateChoreActive,
            onSignOut = viewModel::signOut,
        )
    }
}
