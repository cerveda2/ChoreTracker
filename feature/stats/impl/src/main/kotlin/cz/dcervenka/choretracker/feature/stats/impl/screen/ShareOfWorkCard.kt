package cz.dcervenka.choretracker.feature.stats.impl.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.dcervenka.choretracker.core.design.LocalMemberPalette
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ListGroup
import cz.dcervenka.choretracker.core.model.stats.MemberContribution
import cz.dcervenka.choretracker.core.model.stats.ShareBreakdown

@Composable
internal fun ShareOfWorkCard(
    shareBreakdown: ShareBreakdown,
    memberContributions: List<MemberContribution>,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val palette = LocalMemberPalette.current
    val togetherColor = MaterialTheme.colorScheme.secondary

    ListGroup(modifier = modifier) {
        Column(
            modifier = Modifier.padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Text(
                text = stringResource(R.string.stats_share_title),
                style = MaterialTheme.typography.titleMedium,
            )
            if (shareBreakdown.totalCount == 0) {
                Text(
                    text = stringResource(R.string.stats_empty_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .clip(RoundedCornerShape(10.dp)),
                ) {
                    memberContributions.forEachIndexed { index, contribution ->
                        val percent = shareBreakdown.percentByMemberId[contribution.memberId] ?: 0
                        if (percent > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(percent.toFloat())
                                    .fillMaxHeight()
                                    .background(palette.color(index)),
                            )
                        }
                    }
                    if (shareBreakdown.togetherPercent > 0) {
                        Box(
                            modifier = Modifier
                                .weight(shareBreakdown.togetherPercent.toFloat())
                                .fillMaxHeight()
                                .background(togetherColor),
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(spacing.xSmall),
                ) {
                    // Only members who actually contributed this period - matching the bar above,
                    // which likewise skips a zero-width segment. A household member who hasn't
                    // logged anything yet doesn't need a "· 0%" line cluttering the legend.
                    memberContributions.withIndex().forEach { (index, contribution) ->
                        val percent = shareBreakdown.percentByMemberId[contribution.memberId] ?: 0
                        if (percent > 0) {
                            ShareLegendItem(
                                color = palette.color(index),
                                label = "${contribution.displayName} · $percent%",
                            )
                        }
                    }
                    if (shareBreakdown.togetherPercent > 0) {
                        val togetherLabel = stringResource(R.string.stats_share_together)
                        ShareLegendItem(
                            color = togetherColor,
                            label = "$togetherLabel · ${shareBreakdown.togetherPercent}%",
                        )
                    }
                }
                Text(
                    text = pluralStringResource(
                        R.plurals.stats_share_caption,
                        shareBreakdown.totalCount,
                        shareBreakdown.totalCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ShareLegendItem(color: Color, label: String) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xSmall),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape),
        )
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}
