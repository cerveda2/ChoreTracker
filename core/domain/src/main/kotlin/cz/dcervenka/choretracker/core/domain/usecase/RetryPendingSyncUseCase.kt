package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.SyncRepository
import javax.inject.Inject

class RetryPendingSyncUseCase @Inject constructor(
    private val syncRepository: SyncRepository,
) {
    suspend operator fun invoke(): EmptyResult =
        syncRepository.syncPendingOperations()
}
