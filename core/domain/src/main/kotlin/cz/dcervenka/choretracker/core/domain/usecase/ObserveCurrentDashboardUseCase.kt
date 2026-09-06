package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.data.contract.StatsRepository
import cz.dcervenka.choretracker.core.domain.HouseholdStatisticsCalculator
import cz.dcervenka.choretracker.core.model.stats.DashboardSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject
import kotlin.time.Clock

class ObserveCurrentDashboardUseCase @Inject constructor(
    private val householdRepository: HouseholdRepository,
    private val statsRepository: StatsRepository,
    private val statisticsCalculator: HouseholdStatisticsCalculator,
) {
    operator fun invoke(): Flow<DashboardSnapshot> =
        householdRepository.observeCurrentHousehold()
            .filterNotNull()
            .flatMapLatest { household ->
                statsRepository.observeHouseholdStatsInput(household.id).map { input ->
                    statisticsCalculator.dashboardSnapshot(
                        household = input.household,
                        members = input.members,
                        chores = input.chores,
                        completions = input.completions,
                        today = Clock.System.todayIn(TimeZone.currentSystemDefault()),
                    )
                }
            }
}
