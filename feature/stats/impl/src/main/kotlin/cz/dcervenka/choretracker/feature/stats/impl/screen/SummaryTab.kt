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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.EmptyState
import cz.dcervenka.choretracker.core.design.components.SectionCard
import cz.dcervenka.choretracker.core.model.stats.ChoreStaleness
import cz.dcervenka.choretracker.core.model.stats.ChoreStatus
import cz.dcervenka.choretracker.core.model.stats.HouseholdSummary
import cz.dcervenka.choretracker.core.model.stats.MemberContribution
import cz.dcervenka.choretracker.core.model.stats.StatsSnapshot

@Composable
fun SummaryTab(
    stats: StatsSnapshot,
    contentPadding: PaddingValues,
) {
    val spacing = LocalSpacing.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        item {
            Text(
                text = stats.household.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            SummaryCard(summary = stats.summary)
        }
        item {
            MemberContributionsCard(contributions = stats.memberContributions)
        }
        item {
            StaleChoresCard(staleChores = stats.staleChores)
        }
        if (stats.summary.totalCompletions == 0) {
            item {
                EmptyState(
                    title = stringResource(R.string.stats_empty_title),
                    message = stringResource(R.string.stats_empty_message),
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: HouseholdSummary) {
    SectionCard(title = stringResource(R.string.stats_tab_summary)) {
        if (summary.totalCompletions == 0) {
            Text(
                text = stringResource(R.string.stats_summary_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(R.string.stats_summary_total, summary.totalCompletions),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            summary.topContributor?.let { top ->
                Text(
                    text = stringResource(
                        R.string.stats_summary_top_contributor,
                        top.displayName,
                        top.sharePercent,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun MemberContributionsCard(contributions: List<MemberContribution>) {
    val spacing = LocalSpacing.current
    val memberColors = memberColorPalette()

    SectionCard(title = stringResource(R.string.stats_summary_members_title)) {
        if (contributions.isEmpty()) {
            Text(
                text = stringResource(R.string.stats_summary_members_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            contributions.forEachIndexed { index, member ->
                val barColor = memberColors[index % memberColors.size]
                MemberShareBar(
                    name = member.displayName,
                    sharePercent = member.sharePercent,
                    color = barColor,
                )
                Text(
                    text = stringResource(
                        R.string.stats_summary_member_subline,
                        member.last30DaysCount,
                        member.currentMonthCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (index < contributions.lastIndex) {
                    Box(modifier = Modifier.height(spacing.small))
                }
            }
        }
    }
}

@Composable
private fun MemberShareBar(
    name: String,
    sharePercent: Int,
    color: Color,
) {
    val spacing = LocalSpacing.current
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Text(
            text = name,
            modifier = Modifier.width(88.dp),
            style = MaterialTheme.typography.bodyMedium,
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
                    .fillMaxWidth(fraction = (sharePercent / 100f).coerceIn(0f, 1f))
                    .background(color, CircleShape),
            )
        }
        Text(
            text = stringResource(R.string.dashboard_share_percent, sharePercent),
            modifier = Modifier.width(36.dp),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StaleChoresCard(staleChores: List<ChoreStaleness>) {
    SectionCard(title = stringResource(R.string.stats_summary_attention_title)) {
        val attentionItems = staleChores.filter { chore ->
            chore.status == ChoreStatus.NEEDS_ATTENTION || chore.status == ChoreStatus.NEVER
        }

        if (attentionItems.isEmpty()) {
            Text(
                text = stringResource(R.string.stats_summary_attention_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            attentionItems.forEach { chore ->
                Text(
                    text = stringResource(
                        R.string.stats_summary_attention_line,
                        chore.choreName,
                        stalenessLabel(chore),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun stalenessLabel(chore: ChoreStaleness): String = when (chore.status) {
    ChoreStatus.NEVER -> stringResource(R.string.stats_stale_never)
    ChoreStatus.NEEDS_ATTENTION -> stringResource(R.string.stats_stale_days_ago, chore.daysSinceLastCompletion ?: 0)
    ChoreStatus.SOON -> stringResource(R.string.stats_stale_soon)
    ChoreStatus.OK -> stringResource(R.string.stats_stale_ok)
}

@Composable
internal fun memberColorPalette(): List<Color> = listOf(
    MaterialTheme.colorScheme.primary,
    MaterialTheme.colorScheme.secondary,
    MaterialTheme.colorScheme.tertiary,
    MaterialTheme.colorScheme.error,
)
