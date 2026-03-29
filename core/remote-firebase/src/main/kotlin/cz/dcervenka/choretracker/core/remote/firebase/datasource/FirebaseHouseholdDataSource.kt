package cz.dcervenka.choretracker.core.remote.firebase.datasource

import android.content.Context
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.model.sync.PendingSyncOperation
import cz.dcervenka.choretracker.core.remote.contract.RemoteHouseholdDataSource
import cz.dcervenka.choretracker.core.remote.firebase.runtime.FirebaseRuntimeConfigurator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseHouseholdDataSource @Inject constructor(
    @ApplicationContext context: Context,
) : RemoteHouseholdDataSource {

    init {
        FirebaseRuntimeConfigurator.configure(context)
    }

    override suspend fun pushPendingOperations(operations: List<PendingSyncOperation>): EmptyResult =
        AppResult.Success(Unit)
}
