package cz.dcervenka.choretracker.feature.dashboard.impl.screen

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import cz.dcervenka.choretracker.core.design.components.MemberAvatar
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.stats.BalanceSummary

@Composable
internal fun BalanceCard(
    balance: BalanceSummary?,
    members: List<HouseholdMember>,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    ListGroup(modifier = modifier) {
        Column(
            modifier = Modifier.padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.dashboard_balance_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.dashboard_balance_period),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (balance == null || members.isEmpty()) {
                Text(
                    text = stringResource(R.string.dashboard_balance_even),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                BalanceBody(balance = balance, members = members)
            }
        }
    }
}

@Composable
private fun BalanceBody(balance: BalanceSummary, members: List<HouseholdMember>) {
    val spacing = LocalSpacing.current
    val palette = LocalMemberPalette.current
    val memberIds = members.map { it.id }
    val total = balance.countsByMemberId.values.sum().coerceAtLeast(1)
    // balance.leaderMemberId/laggingMemberId come from a HouseholdStatisticsCalculator snapshot
    // that can lag one combine step behind this composable's own `members` (e.g. right after a
    // removal), so the id may briefly not be in `members` - fall back to the first member rather
    // than indexOfFirst+coerceAtLeast(0), which would keep that fallback's color but still label
    // it with the (nonexistent) leader/lagging member's name.
    val leaderMember = members.find { it.id == balance.leaderMemberId } ?: members.first()
    val laggingMember = members.find { it.id == balance.laggingMemberId } ?: members.first()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        MemberAvatar(
            initial = leaderMember.displayName.take(1),
            color = palette.colorFor(leaderMember.id, memberIds),
            size = 44.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.medium)) {
                members.forEachIndexed { index, member ->
                    val count = balance.countsByMemberId[member.id] ?: 0
                    Text(
                        text = "${member.displayName} · $count",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.color(index),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp)),
            ) {
                members.forEachIndexed { index, member ->
                    val count = balance.countsByMemberId[member.id] ?: 0
                    if (count > 0) {
                        Box(
                            modifier = Modifier
                                .weight(count.toFloat() / total)
                                .fillMaxHeight()
                                .background(palette.color(index)),
                        )
                    }
                }
            }
        }
        if (laggingMember.id != leaderMember.id) {
            MemberAvatar(
                initial = laggingMember.displayName.take(1),
                color = palette.colorFor(laggingMember.id, memberIds),
                size = 44.dp,
            )
        }
    }
    Text(
        text = balanceSentence(balance, members),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun balanceSentence(balance: BalanceSummary, members: List<HouseholdMember>): String {
    if (balance.gap == 0) return stringResource(R.string.dashboard_balance_even)
    val nameById = members.associate { it.id to it.displayName }
    return pluralStringResource(
        R.plurals.dashboard_balance_behind,
        balance.gap,
        nameById[balance.leaderMemberId].orEmpty(),
        nameById[balance.laggingMemberId].orEmpty(),
        balance.gap,
    )
}
