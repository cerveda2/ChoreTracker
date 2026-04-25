package cz.dcervenka.choretracker.feature.stats.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.EmptyState
import cz.dcervenka.choretracker.core.design.components.SectionCard
import cz.dcervenka.choretracker.core.design.toStringRes
import cz.dcervenka.choretracker.core.model.stats.CategoryComparison
import cz.dcervenka.choretracker.core.model.stats.ChoreLeaderResult
import cz.dcervenka.choretracker.core.model.stats.StatsSnapshot

@Composable
fun ByCategoryTab(
    stats: StatsSnapshot,
    contentPadding: PaddingValues,
) {
    val spacing = LocalSpacing.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        if (stats.categoryComparisons.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.stats_category_empty_title),
                    message = stringResource(R.string.stats_category_empty_message),
                )
            }
        } else {
            items(stats.categoryComparisons, key = { it.category.name }) { comparison ->
                CategoryComparisonCard(comparison = comparison)
            }
        }
    }
}

@Composable
private fun CategoryComparisonCard(comparison: CategoryComparison) {
    SectionCard(title = stringResource(comparison.category.toStringRes())) {
        Text(
            text = stringResource(R.string.stats_total_count, comparison.totalCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.stats_category_chore_count, comparison.choreCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        comparison.countsByMember.forEach { (member, count) ->
            Text(text = stringResource(R.string.stats_member_count, member, count))
        }
        Text(
            text = stringResource(R.string.stats_leader, leaderLabel(comparison.leader)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun leaderLabel(leader: ChoreLeaderResult): String = when (leader) {
    ChoreLeaderResult.NoData -> stringResource(R.string.stats_leader_no_data)
    ChoreLeaderResult.Tie -> stringResource(R.string.stats_leader_tie)
    is ChoreLeaderResult.Leader -> leader.displayName
}
