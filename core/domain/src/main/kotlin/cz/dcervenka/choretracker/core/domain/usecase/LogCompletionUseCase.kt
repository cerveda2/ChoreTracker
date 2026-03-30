package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.ChoreCompletionRepository
import javax.inject.Inject

class LogCompletionUseCase @Inject constructor(
    private val choreCompletionRepository: ChoreCompletionRepository,
) {
    suspend operator fun invoke(
        householdId: String,
        choreId: String,
        participantMemberIds: List<String>,
        note: String?,
    ): EmptyResult = choreCompletionRepository.logCompletion(
        householdId = householdId,
        choreId = choreId,
        participantMemberIds = participantMemberIds,
        note = note,
    )
}
