package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.data.contract.StatsRepository
import cz.dcervenka.choretracker.core.domain.HouseholdStatisticsCalculator
import cz.dcervenka.choretracker.core.model.stats.ChoreStaleness
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject
import kotlin.time.Clock

// Staleness-only sibling of ObserveCurrentDashboardUseCase, for callers (like ChoresViewModel)
// that don't need the rest of the dashboard snapshot - contributions, balance, recent
// completions, summary - which dashboardSnapshot computes unconditionally alongside staleness.
class ObserveStaleChoresUseCase @Inject constructor(
    private val householdRepository: HouseholdRepository,
    private val statsRepository: StatsRepository,
    private val statisticsCalculator: HouseholdStatisticsCalculator,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<List<ChoreStaleness>> =
        householdRepository.observeCurrentHousehold()
            .filterNotNull()
            .flatMapLatest { household ->
                statsRepository.observeHouseholdStatsInput(household.id).map { input ->
                    statisticsCalculator.buildStaleness(
                        chores = input.chores,
                        completions = input.completions,
                        members = input.members,
                        timeZone = TimeZone.currentSystemDefault(),
                        today = clock.todayIn(TimeZone.currentSystemDefault()),
                    )
                }
            }
}
