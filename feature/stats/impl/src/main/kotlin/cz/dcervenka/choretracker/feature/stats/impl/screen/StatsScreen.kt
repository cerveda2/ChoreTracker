package cz.dcervenka.choretracker.feature.stats.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
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
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.ChoreTopAppBar
import cz.dcervenka.choretracker.core.design.components.EmptyState
import cz.dcervenka.choretracker.core.design.components.LoadingState
import cz.dcervenka.choretracker.core.design.components.SectionCard
import cz.dcervenka.choretracker.core.formatters.formatMonthLabelForLocale
import cz.dcervenka.choretracker.core.model.stats.ChoreLeaderResult
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
        ChoreScaffold(
            topBar = {
                ChoreTopAppBar(title = stringResource(R.string.stats_title))
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = spacing.large,
                    top = innerPadding.calculateTopPadding() + spacing.large,
                    end = spacing.large,
                    bottom = innerPadding.calculateBottomPadding() + spacing.large,
                ),
                verticalArrangement = Arrangement.spacedBy(spacing.medium),
            ) {
                item {
                    Text(
                        text = stats.household.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (stats.comparisons.isEmpty() && stats.monthlyBreakdown.isEmpty()) {
                    item {
                        EmptyState(
                            title = stringResource(R.string.stats_empty_title),
                            message = stringResource(R.string.stats_empty_message),
                        )
                    }
                } else {
                    items(stats.comparisons, key = { it.choreId }) { comparison ->
                        SectionCard(title = comparison.choreName) {
                            Text(
                                stringResource(R.string.stats_total_count, comparison.totalCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            comparison.countsByMember.forEach { (member, count) ->
                                Text(stringResource(R.string.stats_member_count, member, count))
                            }
                            val leaderText = when (val leader = comparison.leader) {
                                ChoreLeaderResult.NoData -> stringResource(R.string.stats_leader_no_data)
                                ChoreLeaderResult.Tie -> stringResource(R.string.stats_leader_tie)
                                is ChoreLeaderResult.Leader -> leader.displayName
                            }
                            Text(stringResource(R.string.stats_leader, leaderText))
                        }
                    }
                    items(stats.monthlyBreakdown, key = { it.monthLabel }) { month ->
                        SectionCard(title = formatMonthLabelForLocale(month.monthLabel)) {
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
