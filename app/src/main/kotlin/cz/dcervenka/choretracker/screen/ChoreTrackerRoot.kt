package cz.dcervenka.choretracker.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cz.dcervenka.choretracker.core.design.ChoreTrackerTheme
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.feature.auth.impl.navigation.AuthDestination
import cz.dcervenka.choretracker.feature.auth.impl.navigation.authScreen
import cz.dcervenka.choretracker.feature.dashboard.impl.navigation.dashboardScreen
import cz.dcervenka.choretracker.feature.household.impl.navigation.householdScreen
import cz.dcervenka.choretracker.feature.onboarding.impl.navigation.onboardingScreen
import cz.dcervenka.choretracker.feature.settings.impl.navigation.settingsScreen
import cz.dcervenka.choretracker.feature.stats.impl.navigation.statsScreen
import cz.dcervenka.choretracker.navigation.topLevelDestinations
import cz.dcervenka.choretracker.viewmodel.AppViewModel

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
                                label = { Text(stringResource(destination.labelRes)) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = AuthDestination.route,
                modifier = Modifier.padding(padding),
            ) {
                authScreen()
                onboardingScreen()
                dashboardScreen(navController = navController)
                statsScreen()
                householdScreen()
                settingsScreen()
            }
        }
    }
}
