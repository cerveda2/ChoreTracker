package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.SyncRepository
import cz.dcervenka.choretracker.core.model.sync.SyncState
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSyncStateUseCase @Inject constructor(
    private val syncRepository: SyncRepository,
) {
    operator fun invoke(householdId: String): Flow<SyncState?> =
        syncRepository.observeSyncState(householdId)
}
