package cz.dcervenka.choretracker.feature.dashboard.impl.navigation

object DashboardDestination {
    const val route = "dashboard"
}

object DashboardCompletionsDestination {
    const val route = "dashboard/completions"
}

object DashboardCompletionDetailDestination {
    private const val completionIdArg = "completionId"
    const val route = "dashboard/completions/{$completionIdArg}"

    fun createRoute(completionId: String): String = "dashboard/completions/$completionId"
}

object DashboardLogChoreDestination {
    const val route = "dashboard/log"
}
