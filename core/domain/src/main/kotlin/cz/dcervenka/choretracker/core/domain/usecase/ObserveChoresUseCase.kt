package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.ChoreRepository
import cz.dcervenka.choretracker.core.model.chore.Chore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveChoresUseCase @Inject constructor(
    private val choreRepository: ChoreRepository,
) {
    operator fun invoke(householdId: String): Flow<List<Chore>> =
        choreRepository.observeChores(householdId = householdId)
}
