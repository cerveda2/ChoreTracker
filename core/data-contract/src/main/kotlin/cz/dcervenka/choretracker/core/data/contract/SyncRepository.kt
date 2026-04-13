package cz.dcervenka.choretracker.core.data.contract

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.model.sync.SyncState
import kotlinx.coroutines.flow.Flow

interface SyncRepository {
    fun observeSyncState(householdId: String): Flow<SyncState?>

    suspend fun syncPendingOperations(): EmptyResult

    suspend fun restoreHouseholdForUser(userId: String): AppResult<Boolean>
}
