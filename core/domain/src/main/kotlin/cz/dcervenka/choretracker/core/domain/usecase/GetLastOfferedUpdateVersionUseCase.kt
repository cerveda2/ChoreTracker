package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.UpdatePromptRepository
import javax.inject.Inject

class GetLastOfferedUpdateVersionUseCase @Inject constructor(
    private val updatePromptRepository: UpdatePromptRepository,
) {
    suspend operator fun invoke(): Int? = updatePromptRepository.getLastOfferedVersion()
}
