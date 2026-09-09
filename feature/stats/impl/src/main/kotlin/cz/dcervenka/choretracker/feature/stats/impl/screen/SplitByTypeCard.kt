package cz.dcervenka.choretracker.feature.stats.impl.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.dcervenka.choretracker.core.design.LocalMemberPalette
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ListGroup
import cz.dcervenka.choretracker.core.design.toIcon
import cz.dcervenka.choretracker.core.design.toStringRes
import cz.dcervenka.choretracker.core.model.stats.CategoryComparison
import cz.dcervenka.choretracker.core.model.stats.MemberContribution

@Composable
internal fun SplitByTypeCard(
    categoryComparisons: List<CategoryComparison>,
    memberContributions: List<MemberContribution>,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current

    ListGroup(modifier = modifier) {
        Column(
            modifier = Modifier.padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Text(
                text = stringResource(R.string.stats_split_by_type),
                style = MaterialTheme.typography.titleMedium,
            )
            if (categoryComparisons.all { it.totalCount == 0 }) {
                Text(
                    text = stringResource(R.string.stats_empty_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                categoryComparisons.forEach { comparison ->
                    CategorySplitRow(comparison = comparison, memberContributions = memberContributions)
                }
            }
        }
    }
}

@Composable
private fun CategorySplitRow(comparison: CategoryComparison, memberContributions: List<MemberContribution>) {
    val spacing = LocalSpacing.current
    val palette = LocalMemberPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xSmall)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xSmall),
            ) {
                Icon(
                    imageVector = comparison.category.toIcon(),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(comparison.category.toStringRes()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = pluralStringResource(
                    R.plurals.stats_category_chore_count,
                    comparison.choreCount,
                    comparison.choreCount,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (comparison.totalCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
            ) {
                memberContributions.forEachIndexed { index, contribution ->
                    val count = comparison.countsByMemberId[contribution.memberId] ?: 0
                    if (count > 0) {
                        Box(
                            modifier = Modifier
                                .weight(count.toFloat())
                                .fillMaxHeight()
                                .background(palette.color(index)),
                        )
                    }
                }
            }
        }
    }
}
