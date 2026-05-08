package cz.dcervenka.choretracker.feature.stats.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.ChoreTopAppBar
import cz.dcervenka.choretracker.core.design.components.EmptyState
import cz.dcervenka.choretracker.core.formatters.formatInstantForLocale
import cz.dcervenka.choretracker.core.formatters.formatLocalDateForLocale
import cz.dcervenka.choretracker.core.model.stats.RecentCompletion
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

@Composable
fun ChoreHistoryScreen(
    choreName: String,
    completions: List<RecentCompletion>,
    onBack: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val tz = TimeZone.currentSystemDefault()
    val today = remember { Clock.System.now().toLocalDateTime(tz).date }
    val yesterday = remember { today.minus(1, DateTimeUnit.DAY) }
    val todayLabel = stringResource(R.string.dashboard_completions_today)
    val yesterdayLabel = stringResource(R.string.dashboard_completions_yesterday)
    val grouped = remember(completions) {
        completions
            .groupBy { it.completedAt.toLocalDateTime(tz).date }
            .entries
            .sortedByDescending { it.key }
    }

    ChoreScaffold(
        topBar = {
            ChoreTopAppBar(
                title = choreName,
                onBackClick = onBack,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = spacing.large,
                top = innerPadding.calculateTopPadding() + spacing.large,
                end = spacing.large,
                bottom = innerPadding.calculateBottomPadding() + spacing.large,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.large),
        ) {
            if (completions.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.stats_chore_history_empty_title),
                        message = stringResource(R.string.stats_chore_history_empty_message),
                    )
                }
            } else {
                grouped.forEach { (date, items) ->
                    val label = when (date) {
                        today -> todayLabel
                        yesterday -> yesterdayLabel
                        else -> formatLocalDateForLocale(date, "EEEMMMd")
                    }
                    item(key = "group-$date") {
                        ChoreHistoryDateSection(
                            label = label,
                            completions = items,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoreHistoryDateSection(
    label: String,
    completions: List<RecentCompletion>,
) {
    val spacing = LocalSpacing.current
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            HorizontalDivider(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = spacing.small),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            completions.forEach { completion ->
                ChoreHistoryRow(completion = completion)
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
            }
        }
    }
}

@Composable
private fun ChoreHistoryRow(completion: RecentCompletion) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.small),
        verticalArrangement = Arrangement.spacedBy(spacing.xSmall),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = completion.participantNames.joinToString(", ")
                    .ifEmpty { stringResource(R.string.stats_chore_history_unknown) },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = formatInstantForLocale(completion.completedAt, "Hm"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val note = completion.note
        if (!note.isNullOrBlank()) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
