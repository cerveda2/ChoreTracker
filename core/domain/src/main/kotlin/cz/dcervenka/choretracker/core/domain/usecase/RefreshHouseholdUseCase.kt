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
        // Kept even if it fails: a failed push shouldn't skip the pull, which still surfaces
        // whatever the rest of the household changed. The push failure itself is still reported
        // below, unless the pull also fails, in which case that failure takes precedence.
        val pushResult = syncRepository.syncPendingOperations()
        val user = (authRepository.authState.first() as? AuthState.Authenticated)?.user
        if (user == null || user.isPreview) return pushResult
        return when (val pullResult = syncRepository.restoreHouseholdForUser(user.id)) {
            is AppResult.Error -> AppResult.Error(pullResult.message, pullResult.cause)
            is AppResult.Success -> pushResult
        }
    }
}
