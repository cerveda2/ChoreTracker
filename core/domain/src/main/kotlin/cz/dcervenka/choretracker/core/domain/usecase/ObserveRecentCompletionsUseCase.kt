package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.ChoreCompletionRepository
import cz.dcervenka.choretracker.core.model.stats.RecentCompletion
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveRecentCompletionsUseCase @Inject constructor(
    private val choreCompletionRepository: ChoreCompletionRepository,
) {
    operator fun invoke(householdId: String, limit: Int = 10): Flow<List<RecentCompletion>> =
        choreCompletionRepository.observeRecentCompletions(
            householdId = householdId,
            limit = limit,
        )
}
