package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import javax.inject.Inject

class SetMemberRoleUseCase @Inject constructor(
    private val householdRepository: HouseholdRepository,
) {
    suspend operator fun invoke(householdId: String, memberId: String, role: HouseholdRole): EmptyResult =
        householdRepository.setMemberRole(householdId, memberId, role)
}
