package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.ChoreRequestRepository
import javax.inject.Inject

class SendChoreRequestsUseCase @Inject constructor(
    private val choreRequestRepository: ChoreRequestRepository,
) {
    suspend operator fun invoke(
        householdId: String,
        toMemberId: String,
        choreIds: List<String>,
        note: String?,
    ): EmptyResult = choreRequestRepository.sendRequests(householdId, toMemberId, choreIds, note)
}
