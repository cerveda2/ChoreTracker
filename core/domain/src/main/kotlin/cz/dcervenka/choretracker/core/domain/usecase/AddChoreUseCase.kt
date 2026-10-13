package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.ChoreRepository
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
import cz.dcervenka.choretracker.core.model.error.AppError
import javax.inject.Inject

class AddChoreUseCase @Inject constructor(
    private val choreRepository: ChoreRepository,
) {
    suspend operator fun invoke(
        householdId: String,
        name: String,
        category: ChoreCategory,
        frequencyDays: Int? = null,
    ): EmptyResult {
        if (frequencyDays != null && frequencyDays <= 0) {
            return AppResult.Error(AppError.INVALID_FREQUENCY)
        }
        return choreRepository.addChore(
            householdId = householdId,
            name = name,
            category = category,
            frequencyDays = frequencyDays,
        )
    }
}
