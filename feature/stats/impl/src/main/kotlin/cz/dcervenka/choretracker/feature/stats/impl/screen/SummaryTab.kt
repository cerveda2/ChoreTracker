package cz.dcervenka.choretracker.feature.stats.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    SectionCard(title = stringResource(R.string.stats_summary_members_title)) {
        if (contributions.isEmpty()) {
            Text(
                text = stringResource(R.string.stats_summary_members_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            contributions.forEach { member ->
                Text(
                    text = stringResource(
                        R.string.stats_summary_member_line,
                        member.displayName,
                        member.totalCount,
                        member.sharePercent,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(
                        R.string.stats_summary_member_subline,
                        member.last30DaysCount,
                        member.currentMonthCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
