package cz.dcervenka.choretracker.feature.stats.impl.contract

import cz.dcervenka.choretracker.core.common.UiIntent
import cz.dcervenka.choretracker.core.model.stats.StatsPeriod

sealed interface StatsUiIntent : UiIntent {
    data class SelectPeriod(val period: StatsPeriod) : StatsUiIntent
}
