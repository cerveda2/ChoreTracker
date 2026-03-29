package cz.dcervenka.choretracker.core.testing

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.ChoreCompletionRepository
import cz.dcervenka.choretracker.core.data.contract.ChoreRepository
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.data.contract.StatsRepository
import cz.dcervenka.choretracker.core.model.AuthState
import cz.dcervenka.choretracker.core.model.Chore
import cz.dcervenka.choretracker.core.model.DashboardSnapshot
import cz.dcervenka.choretracker.core.model.Household
import cz.dcervenka.choretracker.core.model.HouseholdMember
import cz.dcervenka.choretracker.core.model.Invite
import cz.dcervenka.choretracker.core.model.RecentCompletion
import cz.dcervenka.choretracker.core.model.StatsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAuthRepository(
    initialState: AuthState = AuthState.SignedOut,
) : AuthRepository {
    private val state = MutableStateFlow(initialState)
    override val authState: Flow<AuthState> = state
    override suspend fun signIn(email: String, password: String): EmptyResult = AppResult.Success(Unit)
    override suspend fun signUp(email: String, password: String, displayName: String): EmptyResult = AppResult.Success(Unit)
    override suspend fun continueInPreviewMode(displayName: String): EmptyResult = AppResult.Success(Unit)
    override suspend fun signOut(): EmptyResult = AppResult.Success(Unit)
}

class FakeHouseholdRepository : HouseholdRepository {
    override fun observeCurrentHousehold(): Flow<Household?> = MutableStateFlow(null)
    override fun observeMembers(householdId: String): Flow<List<HouseholdMember>> = MutableStateFlow(emptyList())
    override fun observeInvites(householdId: String): Flow<List<Invite>> = MutableStateFlow(emptyList())
    override suspend fun createHousehold(name: String, ownerDisplayName: String) = AppResult.Error("Not implemented")
    override suspend fun joinHousehold(code: String, currentUserDisplayName: String) = AppResult.Error("Not implemented")
    override suspend fun addMember(householdId: String, displayName: String): EmptyResult = AppResult.Success(Unit)
    override suspend fun createInvite(householdId: String) = AppResult.Error("Not implemented")
}

class FakeChoreRepository : ChoreRepository {
    override fun observeChores(householdId: String): Flow<List<Chore>> = MutableStateFlow(emptyList())
    override suspend fun addChore(householdId: String, name: String): EmptyResult = AppResult.Success(Unit)
    override suspend fun updateChoreActive(choreId: String, isActive: Boolean): EmptyResult = AppResult.Success(Unit)
}

class FakeChoreCompletionRepository : ChoreCompletionRepository {
    override fun observeRecentCompletions(householdId: String, limit: Int): Flow<List<RecentCompletion>> =
        MutableStateFlow(emptyList())

    override suspend fun logCompletion(
        householdId: String,
        choreId: String,
        participantMemberIds: List<String>,
        note: String?,
    ): EmptyResult = AppResult.Success(Unit)
}

class FakeStatsRepository(
    dashboard: DashboardSnapshot,
    stats: StatsSnapshot,
) : StatsRepository {
    override fun observeDashboard(householdId: String): Flow<DashboardSnapshot> = MutableStateFlow(dashboard)
    override fun observeStats(householdId: String): Flow<StatsSnapshot> = MutableStateFlow(stats)
}
