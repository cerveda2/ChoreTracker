package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import javax.inject.Inject

class ContinueInPreviewModeUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(displayName: String): EmptyResult =
        authRepository.continueInPreviewMode(displayName = displayName)
}
