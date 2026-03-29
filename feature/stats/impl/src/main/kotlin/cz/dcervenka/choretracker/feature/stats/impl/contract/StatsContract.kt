package cz.dcervenka.choretracker.feature.stats.impl.contract

import cz.dcervenka.choretracker.core.common.UiState
import cz.dcervenka.choretracker.core.model.stats.StatsSnapshot

data class StatsUiState(
    val snapshot: StatsSnapshot? = null,
) : UiState
