package cz.dcervenka.choretracker.feature.dashboard.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreListRow
import cz.dcervenka.choretracker.core.design.components.CountBadge
import cz.dcervenka.choretracker.core.design.components.DoneButton
import cz.dcervenka.choretracker.core.design.components.IconCircle
import cz.dcervenka.choretracker.core.design.components.ListGroup
import cz.dcervenka.choretracker.core.design.components.SectionHeader
import cz.dcervenka.choretracker.core.design.toIcon
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
import cz.dcervenka.choretracker.core.model.stats.ChoreStaleness

@Composable
internal fun OverdueSection(
    items: List<ChoreStaleness>,
    categoryByChoreId: Map<String, ChoreCategory>,
    onQuickLog: (String) -> Unit,
    onOpenSheet: (String) -> Unit,
) {
    if (items.isEmpty()) return
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        SectionHeader(
            title = stringResource(R.string.dashboard_overdue),
            trailing = { CountBadge(count = items.size) },
        )
        ListGroup {
            items.forEachIndexed { index, stale ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = spacing.medium))
                ChoreListRow(
                    leading = {
                        IconCircle(icon = (categoryByChoreId[stale.choreId] ?: ChoreCategory.OTHER).toIcon())
                    },
                    title = stale.choreName,
                    subtitle = overdueSubtitle(stale),
                    subtitleColor = MaterialTheme.colorScheme.tertiary,
                    trailing = {
                        DoneButton(
                            filled = true,
                            onClick = { onQuickLog(stale.choreId) },
                            onLongClick = { onOpenSheet(stale.choreId) },
                            contentDescription = stringResource(R.string.common_mark_done, stale.choreName),
                        )
                    },
                    onClick = { onQuickLog(stale.choreId) },
                    onLongClick = { onOpenSheet(stale.choreId) },
                )
            }
        }
    }
}

@Composable
private fun overdueSubtitle(stale: ChoreStaleness): String {
    val sinceLast = stale.daysSinceLastCompletion?.let { days ->
        pluralStringResource(R.plurals.dashboard_days_since_last, days, days)
    }
    val frequency = stale.frequencyDays?.let { days ->
        pluralStringResource(R.plurals.log_sheet_meta_frequency, days, days)
    }
    return listOfNotNull(sinceLast, frequency).joinToString(" · ")
}

@Composable
internal fun DueSoonSection(
    items: List<ChoreStaleness>,
    categoryByChoreId: Map<String, ChoreCategory>,
    onQuickLog: (String) -> Unit,
    onOpenSheet: (String) -> Unit,
) {
    if (items.isEmpty()) return
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        SectionHeader(title = stringResource(R.string.dashboard_due_soon))
        ListGroup {
            items.forEachIndexed { index, stale ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = spacing.medium))
                ChoreListRow(
                    leading = {
                        IconCircle(icon = (categoryByChoreId[stale.choreId] ?: ChoreCategory.OTHER).toIcon())
                    },
                    title = stale.choreName,
                    subtitle = dueSoonSubtitle(stale),
                    subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    trailing = {
                        DoneButton(
                            filled = false,
                            onClick = { onQuickLog(stale.choreId) },
                            onLongClick = { onOpenSheet(stale.choreId) },
                            contentDescription = stringResource(R.string.common_mark_done, stale.choreName),
                        )
                    },
                    onClick = { onQuickLog(stale.choreId) },
                    onLongClick = { onOpenSheet(stale.choreId) },
                )
            }
        }
    }
}

@Composable
private fun dueSoonSubtitle(stale: ChoreStaleness): String {
    val dueLabel = stale.dueInDays?.let { days ->
        pluralStringResource(R.plurals.dashboard_due_in_days, days.coerceAtLeast(0), days.coerceAtLeast(0))
    } ?: return stringResource(R.string.dashboard_not_done_yet)
    val lastByLabel = stale.lastCompletedByNames.firstOrNull()?.let { name ->
        stringResource(R.string.dashboard_did_it_last, name)
    }
    return listOfNotNull(dueLabel, lastByLabel).joinToString(" · ")
}
