package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.model.household.Invite
import javax.inject.Inject

class CreateMemberInviteUseCase @Inject constructor(
    private val householdRepository: HouseholdRepository,
) {
    suspend operator fun invoke(householdId: String, memberId: String): AppResult<Invite> =
        householdRepository.createMemberInvite(householdId = householdId, memberId = memberId)
}
