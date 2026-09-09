package cz.dcervenka.choretracker.feature.stats.impl.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.dcervenka.choretracker.core.design.LocalMemberPalette
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ListGroup
import cz.dcervenka.choretracker.core.formatters.formatMonthAbbreviationForLocale
import cz.dcervenka.choretracker.core.model.stats.MemberContribution
import cz.dcervenka.choretracker.core.model.stats.MonthlyBreakdown

@Composable
internal fun MonthlyTrendCard(
    months: List<MonthlyBreakdown>,
    memberContributions: List<MemberContribution>,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val palette = LocalMemberPalette.current
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    // months arrives newest-first, capped at 6 - reverse so bars render left-to-right
    // chronologically, matching normal trend-chart convention.
    val displayMonths = months.asReversed()
    val maxCount = displayMonths.maxOfOrNull { it.totalCount }?.coerceAtLeast(1) ?: 1
    // formatMonthAbbreviationForLocale builds a fresh SimpleDateFormat per call - precompute
    // once per `months` change instead of once per recomposition of this whole card.
    val monthLabels = remember(displayMonths) {
        displayMonths.associate { it.monthLabel to formatMonthAbbreviationForLocale(it.monthLabel) }
    }

    ListGroup(modifier = modifier) {
        Column(
            modifier = Modifier.padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Text(
                text = stringResource(R.string.stats_last_six_months),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                displayMonths.forEach { month ->
                    val fraction = (month.totalCount.toFloat() / maxCount).coerceIn(0.02f, 1f)
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
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(trackColor, RoundedCornerShape(4.dp)),
                            )
                            if (month.totalCount > 0) {
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .fillMaxHeight(fraction),
                                ) {
                                    // Column renders its LAST child flush with its own bottom edge
                                    // (which - via the align+fillMaxHeight above - is the chart's
                                    // true floor), so the member list is walked in reverse to put
                                    // index 0's segment closest to the axis.
                                    memberContributions.withIndex().reversed().forEach { (index, contribution) ->
                                        val count = month.countsByMemberId[contribution.memberId] ?: 0
                                        if (count > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(count.toFloat())
                                                    .fillMaxWidth()
                                                    .background(palette.color(index)),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            text = monthLabels.getValue(month.monthLabel),
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
    }
}
