package cz.dcervenka.choretracker.feature.stats.impl.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.EmptyState
import cz.dcervenka.choretracker.core.design.components.SectionCard
import cz.dcervenka.choretracker.core.model.stats.ChoreComparison
import cz.dcervenka.choretracker.core.model.stats.ChoreLeaderResult
import cz.dcervenka.choretracker.core.model.stats.StatsSnapshot

@Composable
fun ByChoreTab(
    stats: StatsSnapshot,
    contentPadding: PaddingValues,
) {
    val spacing = LocalSpacing.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        if (stats.comparisons.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.stats_per_chore_empty_title),
                    message = stringResource(R.string.stats_per_chore_empty_message),
                )
            }
        } else {
            items(stats.comparisons, key = { it.choreId }) { comparison ->
                ChoreComparisonCard(comparison = comparison)
            }
        }
    }
}

@Composable
private fun ChoreComparisonCard(comparison: ChoreComparison) {
    val spacing = LocalSpacing.current
    val memberColors = memberColorPalette()
    val maxCount = comparison.countsByMember.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    SectionCard(title = comparison.choreName) {
        Text(
            text = stringResource(R.string.stats_total_count, comparison.totalCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        comparison.countsByMember.entries.forEachIndexed { index, (member, count) ->
            val barColor = memberColors[index % memberColors.size]
            val fraction = (count.toFloat() / maxCount).coerceIn(0.02f, 1f)

            Box(modifier = Modifier.height(spacing.xSmall))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                Text(
                    text = member,
                    modifier = Modifier.width(88.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .background(trackColor, CircleShape),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .background(barColor, CircleShape),
                    )
                }
                Text(
                    text = "$count",
                    modifier = Modifier.width(28.dp),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.End,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(modifier = Modifier.height(spacing.xSmall))
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
