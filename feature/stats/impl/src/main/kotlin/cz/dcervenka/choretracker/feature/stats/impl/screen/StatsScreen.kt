package cz.dcervenka.choretracker.feature.stats.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import cz.dcervenka.choretracker.core.design.ChoreTrackerTheme
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.PreviewData
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.ChoreTopAppBar
import cz.dcervenka.choretracker.core.design.components.EmptyState
import cz.dcervenka.choretracker.core.design.components.LoadingState
import cz.dcervenka.choretracker.core.design.components.SectionCard
import cz.dcervenka.choretracker.core.formatters.formatMonthLabelForLocale
import cz.dcervenka.choretracker.core.model.stats.ChoreComparison
import cz.dcervenka.choretracker.core.model.stats.ChoreLeaderResult
import cz.dcervenka.choretracker.core.model.stats.ChoreStaleness
import cz.dcervenka.choretracker.core.model.stats.ChoreStatus
import cz.dcervenka.choretracker.core.model.stats.HouseholdSummary
import cz.dcervenka.choretracker.core.model.stats.MemberContribution
import cz.dcervenka.choretracker.core.model.stats.MonthlyBreakdown
import cz.dcervenka.choretracker.core.model.stats.StatsSnapshot
import cz.dcervenka.choretracker.feature.stats.impl.contract.StatsUiState
import kotlinx.coroutines.launch

@Composable
fun StatsScreen(
    uiState: StatsUiState,
) {
    val spacing = LocalSpacing.current
    val stats = uiState.snapshot

    if (stats == null) {
        LoadingState(message = stringResource(R.string.stats_loading))
        return
    }

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(
        initialPage = selectedTabIndex,
        pageCount = { StatsTab.entries.size },
    )
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        selectedTabIndex = pagerState.currentPage
    }

    ChoreScaffold(
        topBar = {
            ChoreTopAppBar(title = stringResource(R.string.stats_title))
        },
    ) { innerPadding ->
        val pagePadding = PaddingValues(
            start = spacing.large,
            top = spacing.medium,
            end = spacing.large,
            bottom = spacing.large,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.large),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                indicator = {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier
                            .tabIndicatorOffset(selectedTabIndex, matchContentSize = false)
                            .padding(horizontal = spacing.medium),
                        height = spacing.xSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                divider = {},
            ) {
                StatsTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = {
                            selectedTabIndex = index
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = { Text(text = stringResource(tab.labelRes)) },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                when (StatsTab.entries[page]) {
                    StatsTab.Summary -> SummaryTab(
                        stats = stats,
                        contentPadding = pagePadding,
                    )
                    StatsTab.ByChore -> ByChoreTab(
                        stats = stats,
                        contentPadding = pagePadding,
                    )
                    StatsTab.Monthly -> MonthlyTab(
                        stats = stats,
                        contentPadding = pagePadding,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryTab(
    stats: StatsSnapshot,
    contentPadding: PaddingValues,
) {
    val spacing = LocalSpacing.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        item {
            Text(
                text = stats.household.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            SummaryCard(summary = stats.summary)
        }
        item {
            MemberContributionsCard(contributions = stats.memberContributions)
        }
        item {
            StaleChoresCard(staleChores = stats.staleChores)
        }
        if (stats.summary.totalCompletions == 0) {
            item {
                EmptyState(
                    title = stringResource(R.string.stats_empty_title),
                    message = stringResource(R.string.stats_empty_message),
                )
            }
        }
    }
}

@Composable
private fun ByChoreTab(
    stats: StatsSnapshot,
    contentPadding: PaddingValues,
) {
    val spacing = LocalSpacing.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        if (stats.comparisons.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.stats_per_chore_empty_title),
                    message = stringResource(R.string.stats_per_chore_empty_message),
                )
            }
        } else {
            items(stats.comparisons, key = { it.choreId }) { comparison ->
                ChoreComparisonCard(comparison = comparison)
            }
        }
    }
}

@Composable
private fun MonthlyTab(
    stats: StatsSnapshot,
    contentPadding: PaddingValues,
) {
    val spacing = LocalSpacing.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        if (stats.monthlyBreakdown.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.stats_monthly_empty_title),
                    message = stringResource(R.string.stats_monthly_empty_message),
                )
            }
        } else {
            items(stats.monthlyBreakdown, key = { it.monthLabel }) { month ->
                MonthlyBreakdownCard(month = month)
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: HouseholdSummary) {
    SectionCard(title = stringResource(R.string.stats_tab_summary)) {
        if (summary.totalCompletions == 0) {
            Text(
                text = stringResource(R.string.stats_summary_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(R.string.stats_summary_total, summary.totalCompletions),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            summary.topContributor?.let { top ->
                Text(
                    text = stringResource(
                        R.string.stats_summary_top_contributor,
                        top.displayName,
                        top.sharePercent,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun MemberContributionsCard(contributions: List<MemberContribution>) {
    SectionCard(title = stringResource(R.string.stats_summary_members_title)) {
        if (contributions.isEmpty()) {
            Text(
                text = stringResource(R.string.stats_summary_members_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            contributions.forEach { member ->
                Text(
                    text = stringResource(
                        R.string.stats_summary_member_line,
                        member.displayName,
                        member.totalCount,
                        member.sharePercent,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(
                        R.string.stats_summary_member_subline,
                        member.last30DaysCount,
                        member.currentMonthCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StaleChoresCard(staleChores: List<ChoreStaleness>) {
    SectionCard(title = stringResource(R.string.stats_summary_attention_title)) {
        val attentionItems = staleChores.filter { chore ->
            chore.status == ChoreStatus.NEEDS_ATTENTION || chore.status == ChoreStatus.NEVER
        }

        if (attentionItems.isEmpty()) {
            Text(
                text = stringResource(R.string.stats_summary_attention_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            attentionItems.forEach { chore ->
                Text(
                    text = stringResource(
                        R.string.stats_summary_attention_line,
                        chore.choreName,
                        stalenessLabel(chore),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ChoreComparisonCard(comparison: ChoreComparison) {
    SectionCard(title = comparison.choreName) {
        Text(
            text = stringResource(R.string.stats_total_count, comparison.totalCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        comparison.countsByMember.forEach { (member, count) ->
            Text(text = stringResource(R.string.stats_member_count, member, count))
        }
        Text(
            text = stringResource(R.string.stats_leader, leaderLabel(comparison.leader)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun MonthlyBreakdownCard(month: MonthlyBreakdown) {
    SectionCard(title = formatMonthLabelForLocale(month.monthLabel)) {
        Text(
            text = stringResource(R.string.stats_total_count, month.totalCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        month.countsByMember.forEach { (member, count) ->
            Text(text = stringResource(R.string.stats_member_count, member, count))
        }
    }
}

@Composable
private fun leaderLabel(leader: ChoreLeaderResult): String = when (leader) {
    ChoreLeaderResult.NoData -> stringResource(R.string.stats_leader_no_data)
    ChoreLeaderResult.Tie -> stringResource(R.string.stats_leader_tie)
    is ChoreLeaderResult.Leader -> leader.displayName
}

@Composable
private fun stalenessLabel(chore: ChoreStaleness): String = when (chore.status) {
    ChoreStatus.NEVER -> stringResource(R.string.stats_stale_never)
    ChoreStatus.NEEDS_ATTENTION -> {
        stringResource(R.string.stats_stale_days_ago, chore.daysSinceLastCompletion ?: 0)
    }
    ChoreStatus.SOON -> stringResource(R.string.stats_stale_soon)
    ChoreStatus.OK -> stringResource(R.string.stats_stale_ok)
}

private enum class StatsTab(val labelRes: Int) {
    Summary(R.string.stats_tab_summary),
    ByChore(R.string.stats_tab_by_chore),
    Monthly(R.string.stats_tab_monthly),
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun StatsScreenPreview() {
    ChoreTrackerTheme {
        StatsScreen(
            uiState = StatsUiState(snapshot = PreviewData.statsSnapshot),
        )
    }
}
