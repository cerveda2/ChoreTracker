package cz.dcervenka.choretracker.core.sync

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.SyncRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalSyncRepository @Inject constructor() : SyncRepository {
    override suspend fun enqueueHouseholdSync(householdId: String?): EmptyResult = AppResult.Success(Unit)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    abstract fun bindSyncRepository(impl: LocalSyncRepository): SyncRepository
}
