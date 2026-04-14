package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.ChoreRepository
import javax.inject.Inject

class UpdateChoreFrequencyUseCase @Inject constructor(
    private val choreRepository: ChoreRepository,
) {
    suspend operator fun invoke(choreId: String, frequencyDays: Int?): EmptyResult {
        if (frequencyDays != null && frequencyDays <= 0) {
            return AppResult.Error("Frequency must be a positive number.")
        }
        return choreRepository.updateChoreFrequencyDays(choreId = choreId, frequencyDays = frequencyDays)
    }
}
