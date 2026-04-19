package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.ChoreRepository
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
import javax.inject.Inject

class UpdateChoreCategoryUseCase @Inject constructor(
    private val choreRepository: ChoreRepository,
) {
    suspend operator fun invoke(choreId: String, category: ChoreCategory): EmptyResult =
        choreRepository.updateChoreCategory(choreId = choreId, category = category)
}
