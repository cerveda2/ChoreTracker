package cz.dcervenka.choretracker.feature.stats.impl.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cz.dcervenka.choretracker.core.design.LocalSpacing
import cz.dcervenka.choretracker.feature.stats.impl.contract.StatsUiState

@Composable
fun StatsScreen(
    uiState: StatsUiState,
) {
    val spacing = LocalSpacing.current
    val stats = uiState.snapshot

    if (stats == null) {
        Column(modifier = Modifier.fillMaxSize().padding(spacing.large)) {
            Text("Loading statistics...")
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            item {
                Column(modifier = Modifier.padding(spacing.large)) {
                    Text("Statistics", style = MaterialTheme.typography.headlineMedium)
                    Text(stats.household.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
            item {
                Column(modifier = Modifier.padding(horizontal = spacing.large)) {
                    Text("Per chore comparison", style = MaterialTheme.typography.titleLarge)
                }
            }
            items(stats.comparisons, key = { it.choreId }) { comparison ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.large),
                ) {
                    Column(modifier = Modifier.padding(spacing.medium)) {
                        Text(comparison.choreName, style = MaterialTheme.typography.titleMedium)
                        comparison.countsByMember.forEach { (member, count) ->
                            Text("$member: $count")
                        }
                        Text("Leader: ${comparison.leaderLabel}")
                    }
                }
            }
            item {
                Column(modifier = Modifier.padding(horizontal = spacing.large)) {
                    Text("Monthly breakdown", style = MaterialTheme.typography.titleLarge)
                }
            }
            items(stats.monthlyBreakdown, key = { it.monthLabel }) { month ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.large),
                ) {
                    Column(modifier = Modifier.padding(spacing.medium)) {
                        Text(month.monthLabel, style = MaterialTheme.typography.titleMedium)
                        month.countsByMember.forEach { (member, count) ->
                            Text("$member: $count")
                        }
                    }
                }
            }
        }
    }
}
