package cz.dcervenka.choretracker.core.data.contract

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.model.household.Household
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.household.HouseholdRestoreStatus
import cz.dcervenka.choretracker.core.model.household.Invite
import kotlinx.coroutines.flow.Flow

interface HouseholdRepository {
    fun observeCurrentHousehold(): Flow<Household?>
    fun observeRestoreStatus(): Flow<HouseholdRestoreStatus>

    fun observeMembers(householdId: String): Flow<List<HouseholdMember>>

    fun observeInvites(householdId: String): Flow<List<Invite>>

    suspend fun createHousehold(name: String, ownerDisplayName: String): AppResult<Household>

    suspend fun joinHousehold(code: String, currentUserDisplayName: String): AppResult<Household>

    suspend fun addMember(householdId: String, displayName: String): EmptyResult

    suspend fun createInvite(householdId: String): AppResult<Invite>

    suspend fun updateHouseholdName(householdId: String, name: String): EmptyResult

    suspend fun updateCurrentMemberDisplayName(householdId: String, displayName: String): EmptyResult
}
