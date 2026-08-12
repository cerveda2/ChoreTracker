package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.SyncRepository
import cz.dcervenka.choretracker.core.model.auth.AuthState
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class RefreshHouseholdUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
) {
    suspend operator fun invoke(): EmptyResult {
        syncRepository.syncPendingOperations()
        val user = (authRepository.authState.first() as? AuthState.Authenticated)?.user
        if (user == null || user.isPreview) return AppResult.Success(Unit)
        return when (val result = syncRepository.restoreHouseholdForUser(user.id)) {
            is AppResult.Error -> AppResult.Error(result.message, result.cause)
            is AppResult.Success -> AppResult.Success(Unit)
        }
    }
}
