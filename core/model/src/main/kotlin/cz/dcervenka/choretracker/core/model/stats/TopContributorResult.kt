package cz.dcervenka.choretracker.core.model.stats

sealed interface TopContributorResult {
    data object NoData : TopContributorResult
    data object Tie : TopContributorResult
    data class Leader(val displayName: String, val sharePercent: Int) : TopContributorResult
}
