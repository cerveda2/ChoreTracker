package cz.dcervenka.choretracker

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SupervisorAccount
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.design.ChoreTrackerTheme
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.model.AuthState
import cz.dcervenka.choretracker.feature.auth.api.AUTH_ROUTE
import cz.dcervenka.choretracker.feature.auth.impl.authScreen
import cz.dcervenka.choretracker.feature.dashboard.api.DASHBOARD_ROUTE
import cz.dcervenka.choretracker.feature.dashboard.impl.dashboardScreen
import cz.dcervenka.choretracker.feature.household.api.HOUSEHOLD_ROUTE
import cz.dcervenka.choretracker.feature.household.impl.householdScreen
import cz.dcervenka.choretracker.feature.onboarding.api.ONBOARDING_ROUTE
import cz.dcervenka.choretracker.feature.onboarding.impl.onboardingScreen
import cz.dcervenka.choretracker.feature.settings.api.SETTINGS_ROUTE
import cz.dcervenka.choretracker.feature.settings.impl.settingsScreen
import cz.dcervenka.choretracker.feature.stats.api.STATS_ROUTE
import cz.dcervenka.choretracker.feature.stats.impl.statsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

internal enum class RootDestination(val route: String) {
    Auth(AUTH_ROUTE),
    Onboarding(ONBOARDING_ROUTE),
    Main(DASHBOARD_ROUTE),
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(DASHBOARD_ROUTE, "Home", Icons.Outlined.Home),
    TopLevelDestination(STATS_ROUTE, "Stats", Icons.Outlined.Insights),
    TopLevelDestination(HOUSEHOLD_ROUTE, "Household", Icons.Outlined.SupervisorAccount),
    TopLevelDestination(SETTINGS_ROUTE, "Settings", Icons.Outlined.Settings),
)

@HiltViewModel
class AppViewModel @Inject constructor(
    authRepository: AuthRepository,
    householdRepository: HouseholdRepository,
) : ViewModel() {
    internal val rootDestination: StateFlow<RootDestination> = combine(
        authRepository.authState,
        householdRepository.observeCurrentHousehold(),
    ) { authState, household ->
        when {
            authState !is AuthState.Authenticated -> RootDestination.Auth
            household == null -> RootDestination.Onboarding
            else -> RootDestination.Main
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RootDestination.Auth,
    )
}

@Composable
fun ChoreTrackerRoot(
    viewModel: AppViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val rootDestination by viewModel.rootDestination.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = topLevelDestinations.any { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }

    LaunchedEffect(rootDestination) {
        navController.navigate(rootDestination.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = false
            }
            launchSingleTop = true
            restoreState = false
        }
    }

    ChoreTrackerTheme {
        ChoreScaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        topLevelDestinations.forEach { destination ->
                            val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { Text(destination.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = AUTH_ROUTE,
                modifier = Modifier.padding(padding),
            ) {
                authScreen()
                onboardingScreen()
                dashboardScreen()
                statsScreen()
                householdScreen()
                settingsScreen()
            }
        }
    }
}
