package cz.dcervenka.choretracker.core.remote.contract

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.model.chore.ChoreCompletion
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.sync.HouseholdSnapshot
import kotlinx.coroutines.flow.Flow

interface RemoteAuthDataSource {
    val authState: Flow<AuthState>
    val isConfigured: Boolean

    suspend fun signIn(email: String, password: String): EmptyResult

    suspend fun signUp(email: String, password: String, displayName: String): EmptyResult

    suspend fun updateDisplayName(displayName: String): EmptyResult

    suspend fun signOut(): EmptyResult
}

interface RemoteHouseholdDataSource {
    suspend fun upsertHouseholdSnapshot(snapshot: HouseholdSnapshot, userId: String): EmptyResult

    suspend fun upsertMemberSnapshot(
        householdId: String,
        member: HouseholdMember,
        completions: List<ChoreCompletion>,
        userId: String,
    ): EmptyResult

    suspend fun fetchHouseholdSnapshot(userId: String): AppResult<HouseholdSnapshot?>

    suspend fun deleteCompletion(householdId: String, completionId: String): EmptyResult

    suspend fun deleteMember(householdId: String, firestoreDocId: String): EmptyResult
}
