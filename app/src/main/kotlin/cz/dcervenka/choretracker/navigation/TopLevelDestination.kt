package cz.dcervenka.choretracker.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SupervisorAccount
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.feature.dashboard.impl.navigation.DashboardDestination
import cz.dcervenka.choretracker.feature.household.impl.navigation.HouseholdDestination
import cz.dcervenka.choretracker.feature.settings.impl.navigation.SettingsDestination
import cz.dcervenka.choretracker.feature.stats.impl.navigation.StatsDestination

internal data class TopLevelDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
)

internal val topLevelDestinations = listOf(
    TopLevelDestination(DashboardDestination.route, R.string.nav_home, Icons.Outlined.Home),
    TopLevelDestination(StatsDestination.route, R.string.nav_stats, Icons.Outlined.Insights),
    TopLevelDestination(HouseholdDestination.route, R.string.nav_household, Icons.Outlined.SupervisorAccount),
    TopLevelDestination(SettingsDestination.route, R.string.nav_settings, Icons.Outlined.Settings),
)
