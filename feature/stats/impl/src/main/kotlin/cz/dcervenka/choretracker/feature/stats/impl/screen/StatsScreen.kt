package cz.dcervenka.choretracker.feature.stats.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import cz.dcervenka.choretracker.core.design.ChoreTrackerTheme
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.PreviewData
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.LoadingState
import cz.dcervenka.choretracker.core.design.components.SectionCard
import cz.dcervenka.choretracker.feature.stats.impl.contract.StatsUiState

@Composable
fun StatsScreen(
    uiState: StatsUiState,
) {
    val spacing = LocalSpacing.current
    val stats = uiState.snapshot

    if (stats == null) {
        LoadingState(message = stringResource(R.string.stats_loading))
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            item {
                Column(modifier = Modifier.padding(spacing.large)) {
                    Text(stringResource(R.string.stats_title), style = MaterialTheme.typography.headlineMedium)
                    Text(stats.household.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
            item {
                Column(modifier = Modifier.padding(horizontal = spacing.large)) {
                    Text(stringResource(R.string.stats_per_chore_comparison), style = MaterialTheme.typography.titleLarge)
                }
            }
            items(stats.comparisons, key = { it.choreId }) { comparison ->
                SectionCard(
                    title = comparison.choreName,
                    modifier = Modifier.padding(horizontal = spacing.large),
                ) {
                    comparison.countsByMember.forEach { (member, count) ->
                        Text(stringResource(R.string.stats_member_count, member, count))
                    }
                    Text(stringResource(R.string.stats_leader, comparison.leaderLabel))
                }
            }
            item {
                Column(modifier = Modifier.padding(horizontal = spacing.large)) {
                    Text(stringResource(R.string.stats_monthly_breakdown), style = MaterialTheme.typography.titleLarge)
                }
            }
            items(stats.monthlyBreakdown, key = { it.monthLabel }) { month ->
                SectionCard(
                    title = month.monthLabel,
                    modifier = Modifier.padding(horizontal = spacing.large),
                ) {
                    month.countsByMember.forEach { (member, count) ->
                        Text(stringResource(R.string.stats_member_count, member, count))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun StatsScreenPreview() {
    ChoreTrackerTheme {
        StatsScreen(
            uiState = StatsUiState(snapshot = PreviewData.statsSnapshot),
        )
    }
}
