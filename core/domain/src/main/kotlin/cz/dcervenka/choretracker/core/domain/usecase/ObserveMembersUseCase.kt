package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveMembersUseCase @Inject constructor(
    private val householdRepository: HouseholdRepository,
) {
    operator fun invoke(householdId: String): Flow<List<HouseholdMember>> =
        householdRepository.observeMembers(householdId = householdId)
}
