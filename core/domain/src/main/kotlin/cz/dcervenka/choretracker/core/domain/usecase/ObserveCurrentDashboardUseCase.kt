package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.data.contract.StatsRepository
import cz.dcervenka.choretracker.core.domain.HouseholdStatisticsCalculator
import cz.dcervenka.choretracker.core.model.stats.DashboardSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject
import kotlin.time.Clock

class ObserveCurrentDashboardUseCase @Inject constructor(
    private val householdRepository: HouseholdRepository,
    private val statsRepository: StatsRepository,
    private val statisticsCalculator: HouseholdStatisticsCalculator,
    private val observePersonalChoresUseCase: ObservePersonalChoresUseCase,
    private val observePersonalStaleChoresUseCase: ObservePersonalStaleChoresUseCase,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<DashboardSnapshot> =
        householdRepository.observeCurrentHousehold()
            .filterNotNull()
            .flatMapLatest { household ->
                combine(
                    statsRepository.observeHouseholdStatsInput(household.id),
                    observePersonalChoresUseCase(),
                    observePersonalStaleChoresUseCase(),
                ) { input, personalChores, personalStaleness ->
                    val snapshot = statisticsCalculator.dashboardSnapshot(
                        household = household,
                        members = input.members,
                        chores = input.chores,
                        completions = input.completions,
                        today = clock.todayIn(TimeZone.currentSystemDefault()),
                    )
                    // Personal chores only join the due lists, never the household's summary, balance or activity.
                    snapshot.copy(
                        activeChores = snapshot.activeChores + personalChores.filter { it.isActive },
                        staleChores = (snapshot.staleChores + personalStaleness).sortedBy { it.choreName },
                    )
                }
            }
}
