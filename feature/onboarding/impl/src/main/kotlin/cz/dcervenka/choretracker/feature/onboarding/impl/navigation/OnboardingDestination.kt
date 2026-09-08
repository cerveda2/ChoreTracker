package cz.dcervenka.choretracker.feature.onboarding.impl.navigation

// The nested graph wrapping all three screens below, so they can share one OnboardingViewModel
// instance (see sharedOnboardingViewModel in OnboardingNavigation.kt) instead of each getting
// its own via hiltViewModel()'s default per-destination scoping.
object OnboardingGraphDestination {
    const val route = "onboarding_graph"
}

object OnboardingDestination {
    const val route = "onboarding"
}

object QrScanDestination {
    const val route = "onboarding/qr-scan"
}
