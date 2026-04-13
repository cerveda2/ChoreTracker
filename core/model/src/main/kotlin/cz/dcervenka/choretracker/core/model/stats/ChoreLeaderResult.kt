package cz.dcervenka.choretracker.core.model.stats

sealed interface ChoreLeaderResult {
    data object NoData : ChoreLeaderResult
    data object Tie : ChoreLeaderResult
    data class Leader(val displayName: String) : ChoreLeaderResult
}
