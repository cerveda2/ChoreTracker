package cz.dcervenka.choretracker.feature.stats.impl.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
            item {
                SectionCard(title = stringResource(R.string.stats_monthly_trend)) {
                    MonthlyTrendChart(months = stats.monthlyBreakdown)
                }
            }
            items(stats.monthlyBreakdown, key = { it.monthLabel }) { month ->
                MonthlyBreakdownCard(month = month)
            }
        }
    }
}

@Composable
private fun MonthlyTrendChart(months: List<MonthlyBreakdown>) {
    val displayMonths = months.takeLast(12)
    val maxCount = displayMonths.maxOf { it.totalCount }.coerceAtLeast(1)
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        displayMonths.forEach { month ->
            val fraction = (month.totalCount.toFloat() / maxCount).coerceIn(0.02f, 1f)
            val shortLabel = abbreviateMonthLabel(month.monthLabel)

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    text = month.totalCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    maxLines = 1,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(trackColor, RoundedCornerShape(4.dp)),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .background(
                                barColor,
                                RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                            ),
                    )
                }
                Text(
                    text = shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Center,
                )
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

private val MONTH_ABBREVIATIONS = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

private fun abbreviateMonthLabel(rawLabel: String): String {
    val month = rawLabel.substringAfter('-', "").toIntOrNull() ?: return rawLabel
    return MONTH_ABBREVIATIONS.getOrNull(month - 1) ?: rawLabel
}
