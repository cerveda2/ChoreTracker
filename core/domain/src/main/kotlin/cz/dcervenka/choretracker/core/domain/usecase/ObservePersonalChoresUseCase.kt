package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.ChoreRepository
import cz.dcervenka.choretracker.core.model.chore.Chore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObservePersonalChoresUseCase @Inject constructor(
    private val choreRepository: ChoreRepository,
) {
    operator fun invoke(): Flow<List<Chore>> = choreRepository.observePersonalChores()
}
