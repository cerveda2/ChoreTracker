package cz.dcervenka.choretracker.feature.stats.impl.navigation

import android.net.Uri

object StatsDestination {
    const val route = "stats"
}

object StatsChoreHistoryDestination {
    private const val choreIdArg = "choreId"
    private const val choreNameArg = "choreName"
    const val route = "stats/chore/{$choreIdArg}/{$choreNameArg}"

    fun createRoute(choreId: String, choreName: String): String =
        "stats/chore/${Uri.encode(choreId)}/${Uri.encode(choreName)}"
}
