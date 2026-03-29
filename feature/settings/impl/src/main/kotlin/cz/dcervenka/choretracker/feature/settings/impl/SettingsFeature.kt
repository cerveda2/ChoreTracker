package cz.dcervenka.choretracker.feature.settings.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.PrimaryButton
import cz.dcervenka.choretracker.core.design.components.ScreenHeader
import cz.dcervenka.choretracker.core.model.AuthState
import cz.dcervenka.choretracker.feature.settings.api.SETTINGS_ROUTE
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    val userLabel: StateFlow<String> = authRepository.authState.map { state ->
        when (state) {
            is AuthState.Authenticated -> state.user.displayName
            AuthState.RequiresConfiguration -> "Firebase setup required"
            AuthState.SignedOut -> "Signed out"
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "Loading…",
    )

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }
}

fun NavGraphBuilder.settingsScreen() {
    composable(route = SETTINGS_ROUTE) {
        val viewModel: SettingsViewModel = hiltViewModel()
        val userLabel by viewModel.userLabel.collectAsStateWithLifecycle()
        val spacing = LocalSpacing.current

        ChoreScaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(spacing.large),
                verticalArrangement = Arrangement.spacedBy(spacing.medium),
            ) {
                ScreenHeader(title = "Settings")
                Text("Current profile: $userLabel")
                Text(
                    "Dynamic color is reserved for a later toggle. The custom Warm Utility palette is the default.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                PrimaryButton(
                    text = "Sign out",
                    onClick = viewModel::signOut,
                )
            }
        }
    }
}
