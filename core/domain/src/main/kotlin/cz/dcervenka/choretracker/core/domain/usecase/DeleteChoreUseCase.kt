package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.ChoreRepository
import javax.inject.Inject

class DeleteChoreUseCase @Inject constructor(
    private val choreRepository: ChoreRepository,
) {
    suspend operator fun invoke(choreId: String): EmptyResult =
        choreRepository.deleteChore(choreId)
}
