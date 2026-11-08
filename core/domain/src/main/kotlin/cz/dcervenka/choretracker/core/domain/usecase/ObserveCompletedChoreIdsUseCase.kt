package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.ChoreRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveCompletedChoreIdsUseCase @Inject constructor(
    private val choreRepository: ChoreRepository,
) {
    operator fun invoke(householdId: String): Flow<Set<String>> =
        choreRepository.observeCompletedChoreIds(householdId)
}
