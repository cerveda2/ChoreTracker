package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.data.contract.StatsRepository
import cz.dcervenka.choretracker.core.model.stats.DashboardSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

class ObserveCurrentDashboardUseCase @Inject constructor(
    private val householdRepository: HouseholdRepository,
    private val statsRepository: StatsRepository,
) {
    operator fun invoke(): Flow<DashboardSnapshot> =
        householdRepository.observeCurrentHousehold()
            .filterNotNull()
            .flatMapLatest { household -> statsRepository.observeDashboard(household.id) }
}
