package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import javax.inject.Inject

class TransferOwnershipUseCase @Inject constructor(
    private val householdRepository: HouseholdRepository,
) {
    suspend operator fun invoke(householdId: String, newOwnerMemberId: String): EmptyResult =
        householdRepository.transferOwnership(householdId, newOwnerMemberId)
}
