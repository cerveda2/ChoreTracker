package cz.dcervenka.choretracker.feature.chores.impl.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.DoneButton
import cz.dcervenka.choretracker.core.design.components.FreshnessBar
import cz.dcervenka.choretracker.core.design.components.IconCircle
import cz.dcervenka.choretracker.core.design.toIcon
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.stats.ChoreStaleness

// daysSince / frequencyDays, the fraction of a chore's cycle that has elapsed since it was last
// done. Null without a frequency (there is no cycle to measure against) or a last completion.
internal fun freshnessFraction(daysSince: Int?, frequencyDays: Int?): Float? {
    if (daysSince == null || frequencyDays == null || frequencyDays <= 0) return null
    return daysSince.toFloat() / frequencyDays.toFloat()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChoreRow(
    chore: Chore,
    staleness: ChoreStaleness?,
    onQuickLog: () -> Unit,
    onOpenSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val fraction = freshnessFraction(staleness?.daysSinceLastCompletion, chore.frequencyDays)
    val freshnessColor = when {
        fraction == null -> null
        fraction >= 1f -> MaterialTheme.colorScheme.tertiary
        fraction >= 0.8f -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .combinedClickable(onClick = onOpenSheet)
            .padding(start = 16.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        IconCircle(icon = chore.category.toIcon())
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.xSmall),
        ) {
            Text(
                text = chore.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = choreRowSubtitle(chore, staleness),
                style = MaterialTheme.typography.bodySmall,
                color = if (fraction != null && fraction >= 1f) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (fraction != null && freshnessColor != null) {
                FreshnessBar(fraction = fraction, color = freshnessColor)
            }
        }
        DoneButton(
            filled = fraction != null && fraction >= 1f,
            onClick = onQuickLog,
            onLongClick = onOpenSheet,
            contentDescription = stringResource(R.string.common_mark_done, chore.name),
        )
    }
}

@Composable
private fun choreRowSubtitle(chore: Chore, staleness: ChoreStaleness?): String {
    if (!chore.isActive) return stringResource(R.string.chores_filter_paused)
    val frequencyDays = chore.frequencyDays
    val frequency = frequencyDays?.let { pluralStringResource(R.plurals.log_sheet_meta_frequency, it, it) }
    val daysSince = staleness?.daysSinceLastCompletion
    val sinceLast = when {
        daysSince != null -> pluralStringResource(R.plurals.dashboard_days_since_last, daysSince, daysSince)
        frequencyDays != null -> stringResource(R.string.dashboard_not_done_yet)
        else -> null
    }
    return listOfNotNull(frequency, sinceLast).joinToString(" · ")
        .ifBlank { stringResource(R.string.chores_no_schedule) }
}
