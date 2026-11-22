package cz.dcervenka.choretracker.feature.chores.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreListRow
import cz.dcervenka.choretracker.core.design.components.ChoreTurnLine
import cz.dcervenka.choretracker.core.design.components.DoneButton
import cz.dcervenka.choretracker.core.design.components.FreshnessBar
import cz.dcervenka.choretracker.core.design.components.IconCircle
import cz.dcervenka.choretracker.core.design.components.ReminderOffLabel
import cz.dcervenka.choretracker.core.design.components.choreScheduleLabel
import cz.dcervenka.choretracker.core.design.toIcon
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.stats.ChoreStaleness
import cz.dcervenka.choretracker.core.model.stats.ChoreStatus

// The fraction of a chore's period that has elapsed, 1 on the day it's due. Null without a schedule
// (there is no period to measure against) or a last completion.
internal fun freshnessFraction(periodDays: Int?, dueInDays: Int?): Float? {
    if (periodDays == null || dueInDays == null || periodDays <= 0) return null
    return (periodDays - dueInDays).toFloat() / periodDays.toFloat()
}

@Composable
internal fun ChoreRow(
    chore: Chore,
    staleness: ChoreStaleness?,
    onQuickLog: () -> Unit,
    onOpenSheet: () -> Unit,
    modifier: Modifier = Modifier,
    reminderMuted: Boolean = false,
) {
    // A paused chore keeps whatever staleness it had when paused, but shouldn't read as urgent -
    // no fraction at all means no freshness bar, no tertiary tint, and an outlined (not filled)
    // done button below.
    val fraction = if (chore.isActive) {
        freshnessFraction(staleness?.periodDays, staleness?.dueInDays)
    } else {
        null
    }
    // Paired up front so the FreshnessBar lambda below captures two definitely-non-null values,
    // rather than the two separately-nullable locals a plain `if` would smart-cast awkwardly
    // through a deferred composable lambda.
    val freshness: Pair<Float, Color>? = fraction?.let { value -> value to freshnessColor(value) }
    val turn = staleness?.turn?.takeIf { chore.isActive }

    ChoreListRow(
        modifier = modifier,
        leading = { IconCircle(icon = chore.category.toIcon()) },
        title = chore.name,
        subtitle = choreRowSubtitle(chore, staleness),
        subtitleColor = if (fraction != null && fraction >= 1f) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        trailing = {
            DoneButton(
                filled = fraction != null && fraction >= 1f,
                onClick = onQuickLog,
                onLongClick = onOpenSheet,
                contentDescription = stringResource(R.string.common_mark_done, chore.name),
            )
        },
        onClick = onOpenSheet,
        belowSubtitle = if (freshness != null || turn != null || reminderMuted) {
            {
                Column(verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.xSmall)) {
                    freshness?.let { (value, color) -> FreshnessBar(fraction = value, color = color) }
                    turn?.let { ChoreTurnLine(turn = it) }
                    if (reminderMuted) ReminderOffLabel()
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun freshnessColor(fraction: Float): Color = when {
    fraction >= 1f -> MaterialTheme.colorScheme.tertiary
    fraction >= 0.8f -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.primary
}

// A group folds its chores into one row, showing how many there are and the most urgent one's freshness.
@Composable
internal fun ChoreGroupRow(
    name: String,
    chores: List<Chore>,
    staleness: Map<String, ChoreStaleness>,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = chores.filter { it.isActive }
    val overdue = active.count { staleness[it.id]?.status == ChoreStatus.NEEDS_ATTENTION }
    val mostUrgent = active.mapNotNull { freshnessFraction(staleness[it.id]?.periodDays, staleness[it.id]?.dueInDays) }
        .maxOrNull()
    val count = pluralStringResource(R.plurals.chores_group_count, chores.size, chores.size)
    ChoreListRow(
        modifier = modifier,
        leading = { IconCircle(icon = Icons.Outlined.FolderOpen) },
        title = name,
        subtitle = if (overdue > 0) {
            "$count · ${pluralStringResource(R.plurals.chores_group_overdue, overdue, overdue)}"
        } else {
            count
        },
        subtitleColor = if (overdue > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
        trailing = {
            // Done buttons' width, so the chevron and freshness bar line up with the chore rows.
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.chores_group_collapse else R.string.chores_group_expand,
                        name,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        onClick = onToggle,
        belowSubtitle = mostUrgent?.let { value -> { FreshnessBar(fraction = value, color = freshnessColor(value)) } },
    )
}

@Composable
private fun choreRowSubtitle(chore: Chore, staleness: ChoreStaleness?): String {
    if (!chore.isActive) return stringResource(R.string.chores_filter_paused)
    val schedule = chore.schedule
    val frequency = schedule?.let { choreScheduleLabel(it) }
    val daysSince = staleness?.daysSinceLastCompletion
    val sinceLast = when {
        daysSince != null -> pluralStringResource(R.plurals.dashboard_days_since_last, daysSince, daysSince)
        schedule != null -> stringResource(R.string.dashboard_not_done_yet)
        else -> null
    }
    return listOfNotNull(frequency, sinceLast).joinToString(" · ")
        .ifBlank { stringResource(R.string.chores_no_schedule) }
}
