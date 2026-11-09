package cz.dcervenka.choretracker.core.remote.contract

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.chore.ChoreCompletion
import cz.dcervenka.choretracker.core.model.chore.ChoreRequest
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.household.Invite
import cz.dcervenka.choretracker.core.model.sync.HouseholdSnapshot
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface RemoteAuthDataSource {
    val authState: Flow<AuthState>
    val isConfigured: Boolean

    suspend fun signIn(email: String, password: String): EmptyResult

    suspend fun signUp(email: String, password: String, displayName: String): EmptyResult

    suspend fun updateDisplayName(displayName: String): EmptyResult

    suspend fun signOut(): EmptyResult

    suspend fun deleteAccount(): EmptyResult
}

@Suppress("TooManyFunctions")
interface RemoteHouseholdDataSource {
    fun observeMembers(householdId: String, currentUserId: String): Flow<List<HouseholdMember>>

    fun observeCompletions(householdId: String): Flow<List<ChoreCompletion>>

    fun observeInvites(householdId: String): Flow<List<Invite>>

    fun observeChores(householdId: String): Flow<List<Chore>>

    fun observeRequests(householdId: String): Flow<List<ChoreRequest>>

    suspend fun upsertHouseholdSnapshot(snapshot: HouseholdSnapshot, userId: String): EmptyResult

    suspend fun upsertMemberSnapshot(
        householdId: String,
        member: HouseholdMember,
        completions: List<ChoreCompletion>,
        userId: String,
        writeRole: Boolean = false,
    ): EmptyResult

    suspend fun upsertChore(chore: Chore): EmptyResult

    // In one batch write, so every request in a send exists by the time onRequestCreated reads them.
    suspend fun upsertRequests(requests: List<ChoreRequest>): EmptyResult

    suspend fun upsertCompletion(completion: ChoreCompletion): EmptyResult

    suspend fun updateHouseholdName(householdId: String, name: String): EmptyResult

    suspend fun upsertInvite(invite: Invite): EmptyResult

    suspend fun upsertPlaceholder(member: HouseholdMember): EmptyResult

    suspend fun removeMember(householdId: String, memberDocId: String): EmptyResult

    suspend fun setMemberRole(householdId: String, memberDocId: String, role: String): EmptyResult

    suspend fun fetchHouseholdSnapshot(userId: String): AppResult<HouseholdSnapshot?>

    suspend fun fetchInviteByCode(code: String): AppResult<Invite?>

    suspend fun fetchOwnMemberId(householdId: String, userId: String): AppResult<String?>

    suspend fun fetchMembers(householdId: String, currentUserId: String): AppResult<List<HouseholdMember>>

    suspend fun markInviteConsumed(householdId: String, inviteId: String, consumedAt: Instant, consumedByMemberId: String): EmptyResult

    suspend fun deleteCompletion(householdId: String, completionId: String): EmptyResult

    suspend fun transferOwnership(
        householdId: String,
        newOwnerUserId: String,
        newOwnerMemberDocId: String,
        previousOwnerMemberDocId: String,
    ): EmptyResult

    suspend fun leaveHousehold(householdId: String, selfMemberDocId: String): EmptyResult

    suspend fun deleteHousehold(householdId: String): EmptyResult
}
