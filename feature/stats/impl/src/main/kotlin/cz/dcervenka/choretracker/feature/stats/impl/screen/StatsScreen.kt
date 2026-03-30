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
import cz.dcervenka.choretracker.core.design.components.ScreenHeader
import cz.dcervenka.choretracker.core.design.components.SectionCard
import cz.dcervenka.choretracker.core.formatters.formatMonthLabelForLocale
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
                    ScreenHeader(
                        title = stringResource(R.string.stats_title),
                        subtitle = stats.household.name,
                    )
                }
            }
            items(stats.comparisons, key = { it.choreId }) { comparison ->
                SectionCard(
                    title = comparison.choreName,
                    modifier = Modifier.padding(horizontal = spacing.large),
                ) {
                    Text(
                        stringResource(R.string.stats_total_count, comparison.totalCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    comparison.countsByMember.forEach { (member, count) ->
                        Text(stringResource(R.string.stats_member_count, member, count))
                    }
                    Text(stringResource(R.string.stats_leader, comparison.leaderLabel))
                }
            }
            items(stats.monthlyBreakdown, key = { it.monthLabel }) { month ->
                SectionCard(
                    title = formatMonthLabelForLocale(month.monthLabel),
                    modifier = Modifier.padding(horizontal = spacing.large),
                ) {
                    Text(
                        stringResource(R.string.stats_total_count, month.totalCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
