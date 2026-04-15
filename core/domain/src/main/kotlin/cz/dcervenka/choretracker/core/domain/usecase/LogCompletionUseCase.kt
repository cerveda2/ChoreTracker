package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.data.contract.ChoreCompletionRepository
import javax.inject.Inject
import kotlin.time.Instant

class LogCompletionUseCase @Inject constructor(
    private val choreCompletionRepository: ChoreCompletionRepository,
) {
    suspend operator fun invoke(
        householdId: String,
        choreId: String,
        participantMemberIds: List<String>,
        note: String?,
        completedAt: Instant? = null,
    ): AppResult<String> = choreCompletionRepository.logCompletion(
        householdId = householdId,
        choreId = choreId,
        participantMemberIds = participantMemberIds,
        note = note,
        completedAt = completedAt,
    )
}
