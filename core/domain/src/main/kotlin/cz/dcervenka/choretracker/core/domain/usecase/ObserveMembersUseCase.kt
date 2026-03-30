package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveMembersUseCase @Inject constructor(
    private val householdRepository: HouseholdRepository,
) {
    operator fun invoke(householdId: String): Flow<List<HouseholdMember>> =
        householdRepository.observeMembers(householdId = householdId)
}
