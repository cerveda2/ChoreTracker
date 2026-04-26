package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.ChoreCompletionRepository
import javax.inject.Inject

class UpdateCompletionUseCase @Inject constructor(
    private val choreCompletionRepository: ChoreCompletionRepository,
) {
    suspend operator fun invoke(
        completionId: String,
        note: String?,
        participantMemberIds: List<String>,
    ): EmptyResult = choreCompletionRepository.updateCompletion(
        completionId = completionId,
        note = note,
        participantMemberIds = participantMemberIds,
    )
}
