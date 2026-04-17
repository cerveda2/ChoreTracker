package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import javax.inject.Inject

class UpdateDisplayNameUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(displayName: String): EmptyResult =
        authRepository.updateDisplayName(displayName)
}
