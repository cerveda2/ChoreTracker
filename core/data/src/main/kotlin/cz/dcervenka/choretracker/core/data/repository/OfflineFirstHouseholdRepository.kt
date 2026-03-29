package cz.dcervenka.choretracker.core.data.repository

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.data.mapper.asModel
import cz.dcervenka.choretracker.core.database.dao.HouseholdDao
import cz.dcervenka.choretracker.core.database.dao.InviteDao
import cz.dcervenka.choretracker.core.database.dao.MemberDao
import cz.dcervenka.choretracker.core.database.dao.PendingSyncOperationDao
import cz.dcervenka.choretracker.core.database.entity.HouseholdEntity
import cz.dcervenka.choretracker.core.database.entity.InviteEntity
import cz.dcervenka.choretracker.core.database.entity.MemberEntity
import cz.dcervenka.choretracker.core.database.entity.PendingSyncOperationEntity
import cz.dcervenka.choretracker.core.model.auth.AppUser
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.model.household.Household
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import cz.dcervenka.choretracker.core.model.household.Invite
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import java.util.UUID

@Singleton
class OfflineFirstHouseholdRepository @Inject constructor(
    private val householdDao: HouseholdDao,
    private val memberDao: MemberDao,
    private val inviteDao: InviteDao,
    private val pendingSyncOperationDao: PendingSyncOperationDao,
    private val authRepository: AuthRepository,
) : HouseholdRepository {

    override fun observeCurrentHousehold(): Flow<Household?> =
        householdDao.observeCurrentHousehold().map { it?.asModel() }

    override fun observeMembers(householdId: String): Flow<List<HouseholdMember>> =
        memberDao.observeMembers(householdId).map { members -> members.map(MemberEntity::asModel) }

    override fun observeInvites(householdId: String): Flow<List<Invite>> =
        inviteDao.observeInvites(householdId).map { invites -> invites.map(InviteEntity::asModel) }

    override suspend fun createHousehold(name: String, ownerDisplayName: String): AppResult<Household> {
        val user = currentUser() ?: return AppResult.Error("Sign in or continue in preview mode first.")
        val householdId = UUID.randomUUID().toString()
        val invite = generateInvite(householdId)
        val household = HouseholdEntity(
            id = householdId,
            name = name.ifBlank { "My Household" },
            ownerUserId = user.id,
            inviteCode = invite.code,
            createdAt = Clock.System.now(),
        )
        householdDao.upsert(household)
        memberDao.upsert(
            MemberEntity(
                id = UUID.randomUUID().toString(),
                householdId = householdId,
                userId = user.id,
                displayName = ownerDisplayName.ifBlank { user.displayName },
                role = HouseholdRole.OWNER.name,
                isCurrentUser = true,
            ),
        )
        inviteDao.upsert(invite)
        enqueueOperation("household", householdId, "upsert", household.id)
        return AppResult.Success(household.asModel())
    }

    override suspend fun joinHousehold(code: String, currentUserDisplayName: String): AppResult<Household> {
        val invite = inviteDao.findByCode(code.trim())
            ?: return AppResult.Error("No household invite with that code was found.")
        val user = currentUser() ?: return AppResult.Error("Sign in or continue in preview mode first.")
        val existing = memberDao.findByUserId(invite.householdId, user.id)
        if (existing == null) {
            memberDao.upsert(
                MemberEntity(
                    id = UUID.randomUUID().toString(),
                    householdId = invite.householdId,
                    userId = user.id,
                    displayName = currentUserDisplayName.ifBlank { user.displayName },
                    role = HouseholdRole.MEMBER.name,
                    isCurrentUser = true,
                ),
            )
        }
        inviteDao.markConsumed(invite.id, Clock.System.now())
        enqueueOperation("member", invite.householdId, "join", user.id)
        return householdDao.getHousehold(invite.householdId)?.let { AppResult.Success(it.asModel()) }
            ?: AppResult.Error("The household for that invite is no longer available.")
    }

    override suspend fun addMember(householdId: String, displayName: String): EmptyResult {
        memberDao.upsert(
            MemberEntity(
                id = UUID.randomUUID().toString(),
                householdId = householdId,
                userId = null,
                displayName = displayName,
                role = HouseholdRole.MEMBER.name,
                isCurrentUser = false,
            ),
        )
        enqueueOperation("member", householdId, "upsert", displayName)
        return AppResult.Success(Unit)
    }

    override suspend fun createInvite(householdId: String): AppResult<Invite> {
        val invite = generateInvite(householdId)
        householdDao.updateInviteCode(householdId, invite.code)
        inviteDao.upsert(invite)
        enqueueOperation("invite", householdId, "upsert", invite.code)
        return AppResult.Success(invite.asModel())
    }

    private suspend fun currentUser(): AppUser? =
        (authRepository.authState.first() as? AuthState.Authenticated)?.user

    private suspend fun enqueueOperation(
        entityType: String,
        entityId: String,
        operationType: String,
        payload: String,
    ) {
        pendingSyncOperationDao.upsert(
            PendingSyncOperationEntity(
                id = UUID.randomUUID().toString(),
                entityType = entityType,
                entityId = entityId,
                operationType = operationType,
                payload = payload,
                createdAt = Clock.System.now(),
            ),
        )
    }

    private fun generateInvite(householdId: String): InviteEntity =
        InviteEntity(
            id = UUID.randomUUID().toString(),
            householdId = householdId,
            code = UUID.randomUUID().toString().take(8).uppercase(),
            createdAt = Clock.System.now(),
            consumedAt = null,
        )
}
