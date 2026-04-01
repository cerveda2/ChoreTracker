package cz.dcervenka.choretracker.core.data.contract

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult

interface SyncRepository {
    suspend fun syncPendingOperations(): EmptyResult

    suspend fun restoreHouseholdForUser(userId: String): AppResult<Boolean>
}
