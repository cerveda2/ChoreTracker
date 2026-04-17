package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import javax.inject.Inject

class UpdateCurrentMemberDisplayNameUseCase @Inject constructor(
    private val householdRepository: HouseholdRepository,
) {
    suspend operator fun invoke(householdId: String, displayName: String): EmptyResult =
        householdRepository.updateCurrentMemberDisplayName(householdId, displayName)
}
