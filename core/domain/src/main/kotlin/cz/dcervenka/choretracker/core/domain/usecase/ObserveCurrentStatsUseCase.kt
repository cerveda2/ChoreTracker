package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.data.contract.StatsRepository
import cz.dcervenka.choretracker.core.model.stats.StatsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

class ObserveCurrentStatsUseCase @Inject constructor(
    private val householdRepository: HouseholdRepository,
    private val statsRepository: StatsRepository,
) {
    operator fun invoke(): Flow<StatsSnapshot> =
        householdRepository.observeCurrentHousehold()
            .filterNotNull()
            .flatMapLatest { household -> statsRepository.observeStats(household.id) }
}
