package cz.dcervenka.choretracker.navigation

import cz.dcervenka.choretracker.feature.auth.impl.navigation.AuthDestination
import cz.dcervenka.choretracker.feature.dashboard.impl.navigation.DashboardDestination
import cz.dcervenka.choretracker.feature.onboarding.impl.navigation.OnboardingGraphDestination

internal enum class RootDestination(val route: String) {
    Loading("loading"),
    Auth(AuthDestination.route),
    Onboarding(OnboardingGraphDestination.route),
    Main(DashboardDestination.route),
}
