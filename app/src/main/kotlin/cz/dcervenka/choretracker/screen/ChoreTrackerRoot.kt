package cz.dcervenka.choretracker.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cz.dcervenka.choretracker.core.design.ChoreTrackerTheme
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.LoadingState
import cz.dcervenka.choretracker.core.notifications.ui.NotificationPermissionRequest
import cz.dcervenka.choretracker.feature.auth.impl.navigation.authScreen
import cz.dcervenka.choretracker.feature.dashboard.impl.navigation.dashboardScreen
import cz.dcervenka.choretracker.feature.onboarding.impl.navigation.onboardingScreen
import cz.dcervenka.choretracker.feature.settings.impl.navigation.settingsScreen
import cz.dcervenka.choretracker.feature.stats.impl.navigation.statsScreen
import cz.dcervenka.choretracker.navigation.RootDestination
import cz.dcervenka.choretracker.navigation.topLevelDestinations
import cz.dcervenka.choretracker.viewmodel.AppViewModel

@Composable
fun ChoreTrackerRoot(
    viewModel: AppViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val rootDestination by viewModel.rootDestination.collectAsStateWithLifecycle()
    val startDestination = remember(rootDestination) {
        rootDestination.takeIf { it != RootDestination.Loading }?.route
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    val showBottomBar = topLevelDestinations.any { destination -> currentRoute == destination.route }

    // The most recent RootDestination this effect has already navigated for. Seeded with
    // whatever rootDestination already is on this composition's first frame (rememberSaveable
    // restores that same value across a config change), so a rotation that doesn't cross a
    // section boundary - e.g. while several screens deep in Settings - isn't mistaken for a
    // fresh Auth/Onboarding/Main transition. Comparing against navController.currentDestination
    // instead would force-navigate back to that section's start route on every rotation,
    // clobbering the NavController's own restored back stack.
    var lastHandledRootDestination by rememberSaveable { mutableStateOf(rootDestination) }

    LaunchedEffect(rootDestination) {
        if (rootDestination == RootDestination.Loading) return@LaunchedEffect
        if (rootDestination == lastHandledRootDestination) return@LaunchedEffect
        lastHandledRootDestination = rootDestination
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
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                AnimatedVisibility(
                    visible = showBottomBar && rootDestination != RootDestination.Loading,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(300)),
                ) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.background,
                        tonalElevation = 0.dp,
                    ) {
                        topLevelDestinations.forEach { destination ->
                            val selected = currentRoute == destination.route
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
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                ),
                            )
                        }
                    }
                }
            },
        ) { _ ->
            if (rootDestination == RootDestination.Loading) {
                LoadingState(
                    message = stringResource(cz.dcervenka.choretracker.core.design.R.string.common_loading_app),
                    modifier = Modifier,
                )
            } else if (startDestination != null) {
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier,
                    enterTransition = { fadeIn(tween(300)) },
                    exitTransition = { fadeOut(tween(300)) },
                    popEnterTransition = { fadeIn(tween(300)) },
                    popExitTransition = { fadeOut(tween(300)) },
                ) {
                    authScreen()
                    onboardingScreen(navController = navController)
                    dashboardScreen(navController = navController)
                    statsScreen(navController = navController)
                    settingsScreen(navController = navController)
                }
                if (rootDestination == RootDestination.Main) {
                    NotificationPermissionRequest()
                }
            }
        }
    }
}
