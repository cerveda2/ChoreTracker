package cz.dcervenka.choretracker.navigation

import cz.dcervenka.choretracker.feature.auth.api.AUTH_ROUTE
import cz.dcervenka.choretracker.feature.dashboard.api.DASHBOARD_ROUTE
import cz.dcervenka.choretracker.feature.onboarding.api.ONBOARDING_ROUTE

internal enum class RootDestination(val route: String) {
    Auth(AUTH_ROUTE),
    Onboarding(ONBOARDING_ROUTE),
    Main(DASHBOARD_ROUTE),
}
