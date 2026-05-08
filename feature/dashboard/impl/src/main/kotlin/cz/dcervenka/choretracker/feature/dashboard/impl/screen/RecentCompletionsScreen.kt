package cz.dcervenka.choretracker.feature.dashboard.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.ChoreTopAppBar
import cz.dcervenka.choretracker.core.design.components.EmptyState
import cz.dcervenka.choretracker.core.formatters.formatInstantForLocale
import cz.dcervenka.choretracker.core.formatters.formatLocalDateForLocale
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.stats.RecentCompletion
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

@Composable
fun RecentCompletionsScreen(
    completions: List<RecentCompletion>,
    members: List<HouseholdMember>,
    onBack: () -> Unit,
    onOpenCompletion: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    val tz = TimeZone.currentSystemDefault()
    val today = remember { Clock.System.now().toLocalDateTime(tz).date }
    val yesterday = remember { today.minus(1, DateTimeUnit.DAY) }
    val todayLabel = stringResource(R.string.dashboard_completions_today)
    val yesterdayLabel = stringResource(R.string.dashboard_completions_yesterday)

    var selectedMemberId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedChoreName by rememberSaveable { mutableStateOf<String?>(null) }

    val uniqueChoreNames = remember(completions) {
        completions.map { it.choreName }.distinct().sorted()
    }

    val filtered = remember(completions, selectedMemberId, selectedChoreName) {
        completions.filter { c ->
            (selectedMemberId == null || selectedMemberId in c.participantMemberIds) &&
                (selectedChoreName == null || c.choreName == selectedChoreName)
        }
    }

    val grouped = remember(filtered) {
        filtered
            .groupBy { it.completedAt.toLocalDateTime(tz).date }
            .entries
            .sortedByDescending { it.key }
    }

    ChoreScaffold(
        topBar = {
            ChoreTopAppBar(
                title = stringResource(R.string.dashboard_all_completions),
                onBackClick = onBack,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + spacing.large,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.large),
        ) {
            if (members.size > 1) {
                item(key = "member-filter") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = spacing.large),
                        horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    ) {
                        item {
                            FilterChip(
                                selected = selectedMemberId == null,
                                onClick = { selectedMemberId = null },
                                label = { Text(stringResource(R.string.dashboard_filter_all)) },
                            )
                        }
                        items(members, key = { it.id }) { member ->
                            FilterChip(
                                selected = selectedMemberId == member.id,
                                onClick = {
                                    selectedMemberId = if (selectedMemberId == member.id) null else member.id
                                },
                                label = { Text(member.displayName) },
                            )
                        }
                    }
                }
            }

            if (uniqueChoreNames.size > 1) {
                item(key = "chore-filter") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = spacing.large),
                        horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    ) {
                        item {
                            FilterChip(
                                selected = selectedChoreName == null,
                                onClick = { selectedChoreName = null },
                                label = { Text(stringResource(R.string.dashboard_filter_all)) },
                            )
                        }
                        items(uniqueChoreNames, key = { it }) { choreName ->
                            FilterChip(
                                selected = selectedChoreName == choreName,
                                onClick = {
                                    selectedChoreName = if (selectedChoreName == choreName) null else choreName
                                },
                                label = { Text(choreName) },
                            )
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyState(
                        modifier = Modifier.padding(horizontal = spacing.large),
                        title = stringResource(R.string.dashboard_recent_completions_empty_title),
                        message = stringResource(R.string.dashboard_recent_completions_empty_message),
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
                        CompletionDateSection(
                            modifier = Modifier.padding(horizontal = spacing.large),
                            label = label,
                            completions = items,
                            onOpenCompletion = onOpenCompletion,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletionDateSection(
    label: String,
    completions: List<RecentCompletion>,
    onOpenCompletion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier,
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
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            completions.forEach { completion ->
                RecentCompletionRow(
                    completion = completion,
                    onClick = { onOpenCompletion(completion.completionId) },
                    trailingText = formatInstantForLocale(completion.completedAt, "Hm"),
                    emphasizeAsActivity = true,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp + spacing.small),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
            }
        }
    }
}
