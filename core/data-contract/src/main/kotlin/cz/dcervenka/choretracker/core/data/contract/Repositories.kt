package cz.dcervenka.choretracker.core.data.contract

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.model.AuthState
import cz.dcervenka.choretracker.core.model.Chore
import cz.dcervenka.choretracker.core.model.DashboardSnapshot
import cz.dcervenka.choretracker.core.model.Household
import cz.dcervenka.choretracker.core.model.HouseholdMember
import cz.dcervenka.choretracker.core.model.Invite
import cz.dcervenka.choretracker.core.model.RecentCompletion
import cz.dcervenka.choretracker.core.model.StatsSnapshot
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val authState: Flow<AuthState>

    suspend fun signIn(email: String, password: String): EmptyResult

    suspend fun signUp(email: String, password: String, displayName: String): EmptyResult

    suspend fun continueInPreviewMode(displayName: String): EmptyResult

    suspend fun signOut(): EmptyResult
}

interface HouseholdRepository {
    fun observeCurrentHousehold(): Flow<Household?>

    fun observeMembers(householdId: String): Flow<List<HouseholdMember>>

    fun observeInvites(householdId: String): Flow<List<Invite>>

    suspend fun createHousehold(name: String, ownerDisplayName: String): AppResult<Household>

    suspend fun joinHousehold(code: String, currentUserDisplayName: String): AppResult<Household>

    suspend fun addMember(householdId: String, displayName: String): EmptyResult

    suspend fun createInvite(householdId: String): AppResult<Invite>
}

interface ChoreRepository {
    fun observeChores(householdId: String): Flow<List<Chore>>

    suspend fun addChore(householdId: String, name: String): EmptyResult

    suspend fun updateChoreActive(choreId: String, isActive: Boolean): EmptyResult
}

interface ChoreCompletionRepository {
    fun observeRecentCompletions(householdId: String, limit: Int = 10): Flow<List<RecentCompletion>>

    suspend fun logCompletion(
        householdId: String,
        choreId: String,
        participantMemberIds: List<String>,
        note: String?,
    ): EmptyResult
}

interface StatsRepository {
    fun observeDashboard(householdId: String): Flow<DashboardSnapshot>

    fun observeStats(householdId: String): Flow<StatsSnapshot>
}

interface SyncRepository {
    suspend fun enqueueHouseholdSync(householdId: String?): EmptyResult
}
