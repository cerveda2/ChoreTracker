package cz.dcervenka.choretracker.core.data.contract

import cz.dcervenka.choretracker.core.common.EmptyResult

interface SyncRepository {
    suspend fun enqueueHouseholdSync(householdId: String?): EmptyResult
}
