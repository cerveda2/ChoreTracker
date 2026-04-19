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
import cz.dcervenka.choretracker.core.formatters.formatMonthLabelForLocale
import cz.dcervenka.choretracker.core.model.stats.MonthlyBreakdown
import cz.dcervenka.choretracker.core.model.stats.StatsSnapshot

@Composable
fun MonthlyTab(
    stats: StatsSnapshot,
    contentPadding: PaddingValues,
) {
    val spacing = LocalSpacing.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        if (stats.monthlyBreakdown.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.stats_monthly_empty_title),
                    message = stringResource(R.string.stats_monthly_empty_message),
                )
            }
        } else {
            items(stats.monthlyBreakdown, key = { it.monthLabel }) { month ->
                MonthlyBreakdownCard(month = month)
            }
        }
    }
}

@Composable
private fun MonthlyBreakdownCard(month: MonthlyBreakdown) {
    SectionCard(title = formatMonthLabelForLocale(month.monthLabel)) {
        Text(
            text = stringResource(R.string.stats_total_count, month.totalCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        month.countsByMember.forEach { (member, count) ->
            Text(text = stringResource(R.string.stats_member_count, member, count))
        }
    }
}
