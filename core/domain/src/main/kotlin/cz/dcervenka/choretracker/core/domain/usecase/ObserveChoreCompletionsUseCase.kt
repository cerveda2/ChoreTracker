package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.ChoreCompletionRepository
import cz.dcervenka.choretracker.core.model.stats.RecentCompletion
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveChoreCompletionsUseCase @Inject constructor(
    private val choreCompletionRepository: ChoreCompletionRepository,
) {
    operator fun invoke(householdId: String, choreId: String): Flow<List<RecentCompletion>> =
        choreCompletionRepository.observeCompletionsByChore(
            householdId = householdId,
            choreId = choreId,
        )
}
