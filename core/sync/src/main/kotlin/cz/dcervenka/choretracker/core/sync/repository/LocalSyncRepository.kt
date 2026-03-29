package cz.dcervenka.choretracker.core.sync.repository

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.SyncRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalSyncRepository @Inject constructor() : SyncRepository {
    override suspend fun enqueueHouseholdSync(householdId: String?): EmptyResult = AppResult.Success(Unit)
}
