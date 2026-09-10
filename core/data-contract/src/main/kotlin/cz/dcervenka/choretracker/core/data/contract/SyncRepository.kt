package cz.dcervenka.choretracker.core.data.contract

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.model.sync.SyncState
import kotlinx.coroutines.flow.Flow

interface SyncRepository {
    fun observeSyncState(householdId: String): Flow<SyncState?>

    suspend fun syncPendingOperations(): EmptyResult

    suspend fun restoreHouseholdForUser(userId: String): AppResult<Boolean>

    suspend fun ensureInviteLocal(code: String): EmptyResult

    /** Atomically hands household ownership to another linked member. */
    suspend fun transferOwnership(
        householdId: String,
        newOwnerUserId: String,
        newOwnerMemberDocId: String,
        previousOwnerMemberDocId: String,
    ): EmptyResult

    /** Soft-removes the caller's own membership remotely, then wipes local data. */
    suspend fun leaveHousehold(householdId: String, selfMemberDocId: String): EmptyResult
}
