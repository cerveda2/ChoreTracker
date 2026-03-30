package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.model.household.Household
import javax.inject.Inject

class JoinHouseholdUseCase @Inject constructor(
    private val householdRepository: HouseholdRepository,
) {
    suspend operator fun invoke(code: String, currentUserDisplayName: String): AppResult<Household> =
        householdRepository.joinHousehold(
            code = code,
            currentUserDisplayName = currentUserDisplayName,
        )
}
