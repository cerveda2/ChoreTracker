package cz.dcervenka.choretracker.feature.stats.impl.screen

import cz.dcervenka.choretracker.core.design.R

internal enum class StatsTab(val labelRes: Int) {
    Summary(R.string.stats_tab_summary),
    ByChore(R.string.stats_tab_by_chore),
    ByCategory(R.string.stats_tab_by_category),
    Monthly(R.string.stats_tab_monthly),
}
