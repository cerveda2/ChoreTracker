package cz.dcervenka.choretracker.core.remote.contract

import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.model.AuthState
import cz.dcervenka.choretracker.core.model.PendingSyncOperation
import kotlinx.coroutines.flow.Flow

interface RemoteAuthDataSource {
    val authState: Flow<AuthState>
    val isConfigured: Boolean

    suspend fun signIn(email: String, password: String): EmptyResult

    suspend fun signUp(email: String, password: String, displayName: String): EmptyResult

    suspend fun signOut(): EmptyResult
}

interface RemoteHouseholdDataSource {
    suspend fun pushPendingOperations(operations: List<PendingSyncOperation>): EmptyResult
}
