package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.ChoreRequestRepository
import javax.inject.Inject

class CancelChoreRequestUseCase @Inject constructor(
    private val choreRequestRepository: ChoreRequestRepository,
) {
    suspend operator fun invoke(requestId: String): EmptyResult = choreRequestRepository.cancelRequest(requestId)
}
