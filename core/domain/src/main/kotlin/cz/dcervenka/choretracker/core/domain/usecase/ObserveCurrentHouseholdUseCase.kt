package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.model.household.Household
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveCurrentHouseholdUseCase @Inject constructor(
    private val householdRepository: HouseholdRepository,
) {
    operator fun invoke(): Flow<Household?> = householdRepository.observeCurrentHousehold()
}
