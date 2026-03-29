package cz.dcervenka.choretracker.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SupervisorAccount
import androidx.compose.ui.graphics.vector.ImageVector
import cz.dcervenka.choretracker.feature.dashboard.api.DASHBOARD_ROUTE
import cz.dcervenka.choretracker.feature.household.api.HOUSEHOLD_ROUTE
import cz.dcervenka.choretracker.feature.settings.api.SETTINGS_ROUTE
import cz.dcervenka.choretracker.feature.stats.api.STATS_ROUTE

internal data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

internal val topLevelDestinations = listOf(
    TopLevelDestination(DASHBOARD_ROUTE, "Home", Icons.Outlined.Home),
    TopLevelDestination(STATS_ROUTE, "Stats", Icons.Outlined.Insights),
    TopLevelDestination(HOUSEHOLD_ROUTE, "Household", Icons.Outlined.SupervisorAccount),
    TopLevelDestination(SETTINGS_ROUTE, "Settings", Icons.Outlined.Settings),
)
