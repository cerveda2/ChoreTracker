package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.model.household.Household
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveCurrentHouseholdUseCase @Inject constructor(
    private val householdRepository: HouseholdRepository,
) {
    operator fun invoke(): Flow<Household?> = householdRepository.observeCurrentHousehold()
}
