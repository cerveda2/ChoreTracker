package cz.dcervenka.choretracker.feature.stats.impl.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import cz.dcervenka.choretracker.core.design.LocalMemberPalette
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.EmptyState
import cz.dcervenka.choretracker.core.design.components.ListGroup
import cz.dcervenka.choretracker.core.design.components.SectionHeader
import cz.dcervenka.choretracker.core.model.stats.ChoreComparison
import cz.dcervenka.choretracker.core.model.stats.MemberContribution

@Composable
internal fun ByChoreSection(
    comparisons: List<ChoreComparison>,
    memberContributions: List<MemberContribution>,
    onChoreClick: (choreId: String, choreName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        SectionHeader(title = stringResource(R.string.stats_by_chore))
        if (comparisons.isEmpty()) {
            ListGroup {
                EmptyState(
                    title = stringResource(R.string.stats_empty_title),
                    message = stringResource(R.string.stats_empty_message),
                    modifier = Modifier.padding(spacing.medium),
                )
            }
        } else {
            ListGroup {
                comparisons.forEachIndexed { index, comparison ->
                    if (index > 0) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = spacing.medium))
                    }
                    ChoreComparisonRow(
                        comparison = comparison,
                        memberContributions = memberContributions,
                        onClick = { onChoreClick(comparison.choreId, comparison.choreName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoreComparisonRow(
    comparison: ChoreComparison,
    memberContributions: List<MemberContribution>,
    onClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val palette = LocalMemberPalette.current
    val nextTurnName = comparison.nextTurnMemberId
        ?.let { id -> memberContributions.find { it.memberId == id }?.displayName }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.xSmall),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = comparison.choreName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (nextTurnName != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = stringResource(R.string.stats_turn, nextTurnName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = spacing.small, vertical = spacing.xSmall),
                    )
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.medium)) {
            memberContributions.forEachIndexed { index, contribution ->
                val count = comparison.countsByMemberId[contribution.memberId] ?: 0
                Text(
                    text = "${contribution.displayName} · $count",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.color(index),
                )
            }
        }
    }
}
