package cz.dcervenka.choretracker.navigation

import cz.dcervenka.choretracker.feature.auth.impl.navigation.AuthDestination
import cz.dcervenka.choretracker.feature.dashboard.impl.navigation.DashboardDestination
import cz.dcervenka.choretracker.feature.onboarding.impl.navigation.OnboardingDestination

internal enum class RootDestination(val route: String) {
    Loading("loading"),
    Auth(AuthDestination.route),
    Onboarding(OnboardingDestination.route),
    Main(DashboardDestination.route),
}
