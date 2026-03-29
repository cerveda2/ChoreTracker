package cz.dcervenka.choretracker.feature.auth.impl.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import cz.dcervenka.choretracker.feature.auth.api.AUTH_ROUTE
import cz.dcervenka.choretracker.feature.auth.impl.screen.AuthScreen
import cz.dcervenka.choretracker.feature.auth.impl.viewmodel.AuthViewModel

fun NavGraphBuilder.authScreen() {
    composable(route = AUTH_ROUTE) {
        val viewModel: AuthViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        AuthScreen(
            uiState = uiState.value,
            onIntent = viewModel::dispatch,
        )
    }
}
