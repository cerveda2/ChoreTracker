package cz.dcervenka.choretracker.feature.stats.impl.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
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
import androidx.compose.ui.unit.dp
import cz.dcervenka.choretracker.core.design.ChoreTrackerTheme
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.PreviewData
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.ChoreTopAppBar
import cz.dcervenka.choretracker.core.design.components.LoadingState
import cz.dcervenka.choretracker.core.design.components.TopLevelBottomBarSpacer
import cz.dcervenka.choretracker.feature.stats.impl.contract.StatsUiState
import kotlinx.coroutines.launch

@Composable
fun StatsScreen(
    uiState: StatsUiState,
    onChoreClick: (choreId: String, choreName: String) -> Unit = { _, _ -> },
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
        bottomBar = { TopLevelBottomBarSpacer() },
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
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding(),
                ),
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.large),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                indicator = { tabPositions ->
                    with(TabRowDefaults) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier
                                .tabIndicatorOffset(tabPositions[selectedTabIndex])
                                .padding(horizontal = spacing.medium),
                            height = spacing.xSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                divider = {},
                edgePadding = 0.dp,
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
                    StatsTab.Summary -> SummaryTab(stats = stats, contentPadding = pagePadding)
                    StatsTab.ByChore -> ByChoreTab(
                        stats = stats,
                        contentPadding = pagePadding,
                        onChoreClick = onChoreClick,
                    )
                    StatsTab.ByCategory -> ByCategoryTab(stats = stats, contentPadding = pagePadding)
                    StatsTab.Monthly -> MonthlyTab(stats = stats, contentPadding = pagePadding)
                }
            }
        }
    }
}

internal enum class StatsTab(val labelRes: Int) {
    Summary(R.string.stats_tab_summary),
    ByChore(R.string.stats_tab_by_chore),
    ByCategory(R.string.stats_tab_by_category),
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
