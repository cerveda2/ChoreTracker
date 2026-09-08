package cz.dcervenka.choretracker.feature.stats.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import cz.dcervenka.choretracker.core.design.ChoreTrackerTheme
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.core.design.PreviewData
import cz.dcervenka.choretracker.core.design.R
import cz.dcervenka.choretracker.core.design.components.ChoreScaffold
import cz.dcervenka.choretracker.core.design.components.ChoreTopAppBar
import cz.dcervenka.choretracker.core.design.components.LoadingState
import cz.dcervenka.choretracker.core.design.components.TopLevelBottomBarSpacer
import cz.dcervenka.choretracker.core.model.stats.StatsPeriod
import cz.dcervenka.choretracker.feature.stats.impl.contract.StatsUiIntent
import cz.dcervenka.choretracker.feature.stats.impl.contract.StatsUiState

@Composable
fun StatsScreen(
    uiState: StatsUiState,
    onIntent: (StatsUiIntent) -> Unit = {},
    onChoreClick: (choreId: String, choreName: String) -> Unit = { _, _ -> },
) {
    val spacing = LocalSpacing.current
    val stats = uiState.snapshot

    ChoreScaffold(
        topBar = {
            ChoreTopAppBar(title = stringResource(R.string.stats_title))
        },
        bottomBar = { TopLevelBottomBarSpacer() },
    ) { innerPadding ->
        if (stats == null) {
            LoadingState(
                message = stringResource(R.string.stats_loading),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = spacing.large,
                    top = innerPadding.calculateTopPadding() + spacing.medium,
                    end = spacing.large,
                    bottom = innerPadding.calculateBottomPadding() + spacing.large,
                ),
                verticalArrangement = Arrangement.spacedBy(spacing.large),
            ) {
                item(key = "period") {
                    PeriodSelector(
                        selected = uiState.period,
                        onSelect = { onIntent(StatsUiIntent.SelectPeriod(it)) },
                    )
                }
                item(key = "share") {
                    ShareOfWorkCard(
                        shareBreakdown = stats.shareBreakdown,
                        memberContributions = stats.memberContributions,
                    )
                }
                item(key = "monthly") {
                    MonthlyTrendCard(
                        months = stats.monthlyBreakdown,
                        memberContributions = stats.memberContributions,
                    )
                }
                item(key = "split") {
                    SplitByTypeCard(
                        categoryComparisons = stats.categoryComparisons,
                        memberContributions = stats.memberContributions,
                    )
                }
                item(key = "by-chore") {
                    ByChoreSection(
                        comparisons = stats.comparisons,
                        memberContributions = stats.memberContributions,
                        onChoreClick = onChoreClick,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSelector(
    selected: StatsPeriod,
    onSelect: (StatsPeriod) -> Unit,
) {
    val options = StatsPeriod.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, period ->
            SegmentedButton(
                selected = selected == period,
                onClick = { onSelect(period) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = {
                    Text(
                        text = stringResource(period.labelRes()),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

private fun StatsPeriod.labelRes(): Int = when (this) {
    StatsPeriod.WEEK -> R.string.stats_period_week
    StatsPeriod.MONTH -> R.string.stats_period_month
    StatsPeriod.SIX_MONTHS -> R.string.stats_period_six_months
    StatsPeriod.ALL -> R.string.stats_period_all
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun StatsScreenPreview() {
    ChoreTrackerTheme {
        StatsScreen(uiState = StatsUiState(snapshot = PreviewData.statsSnapshot))
    }
}
