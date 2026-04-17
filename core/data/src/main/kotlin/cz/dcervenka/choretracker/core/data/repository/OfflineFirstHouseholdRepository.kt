package cz.dcervenka.choretracker.core.data.repository

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.data.contract.SyncRepository
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
import cz.dcervenka.choretracker.core.model.household.HouseholdRestoreStatus
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import cz.dcervenka.choretracker.core.model.household.Invite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

private const val INVITE_CODE_LENGTH = 8

@Singleton
class OfflineFirstHouseholdRepository @Inject constructor(
    private val householdDao: HouseholdDao,
    private val memberDao: MemberDao,
    private val inviteDao: InviteDao,
    private val pendingSyncOperationDao: PendingSyncOperationDao,
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
) : HouseholdRepository {

    private val restoreStatus = MutableStateFlow(HouseholdRestoreStatus())

    override fun observeCurrentHousehold(): Flow<Household?> = authRepository.authState.flatMapLatest { authState ->
        flow {
            val user = (authState as? AuthState.Authenticated)?.user
            if (user != null && !user.isPreview) {
                syncRepository.syncPendingOperations()
                if (householdDao.getCurrentHouseholdForUser(user.id) == null) {
                    Timber.d("observeCurrentHousehold: no local household, restoring for user=${user.id}")
                    restoreStatus.value = HouseholdRestoreStatus(isRestoring = true)
                    when (val restoreResult = syncRepository.restoreHouseholdForUser(user.id)) {
                        is AppResult.Error -> {
                            Timber.e("observeCurrentHousehold: restore failed - ${restoreResult.message}")
                            restoreStatus.value = HouseholdRestoreStatus(
                                isRestoring = false,
                                errorMessage = restoreResult.message,
                            )
                        }
                        is AppResult.Success -> {
                            Timber.d("observeCurrentHousehold: restore succeeded")
                            restoreStatus.value = HouseholdRestoreStatus()
                        }
                    }
                } else {
                    restoreStatus.value = HouseholdRestoreStatus()
                }
                emitAll(householdDao.observeHouseholdForUser(user.id).map { it?.asModel() })
            } else if (user != null && user.isPreview) {
                restoreStatus.value = HouseholdRestoreStatus()
                emit(
                    Household(
                        id = "preview-household",
                        name = "Sunny Flat",
                        ownerUserId = "preview-user",
                        inviteCode = "HOME42",
                        createdAt = Clock.System.now(),
                    ),
                )
            } else {
                restoreStatus.value = HouseholdRestoreStatus()
                emit(null)
            }
        }
    }

    override fun observeRestoreStatus(): Flow<HouseholdRestoreStatus> = restoreStatus

    override fun observeMembers(householdId: String): Flow<List<HouseholdMember>> =
        memberDao.observeMembers(householdId).map { members -> members.map(MemberEntity::asModel) }

    override fun observeInvites(householdId: String): Flow<List<Invite>> =
        inviteDao.observeInvites(householdId).map { invites -> invites.map(InviteEntity::asModel) }

    override suspend fun createHousehold(name: String, ownerDisplayName: String): AppResult<Household> {
        Timber.d("createHousehold: name=$name ownerDisplayName=$ownerDisplayName")
        val user = currentUser() ?: return AppResult.Error("Sign in or continue in preview mode first.").also {
            Timber.w("createHousehold failed: user not authenticated")
        }
        if (user.isPreview) {
            return AppResult.Error("Cannot create household in preview mode.").also {
                Timber.w("createHousehold failed: preview user attempted write operation")
            }
        }
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
        syncRepository.syncPendingOperations()
        return AppResult.Success(household.asModel())
    }

    override suspend fun joinHousehold(code: String, currentUserDisplayName: String): AppResult<Household> {
        Timber.d("joinHousehold: code=$code displayName=$currentUserDisplayName")
        val invite = inviteDao.findByCode(code.trim())
        val user = currentUser()
        return when {
            invite == null -> AppResult.Error("No household invite with that code was found.").also {
                Timber.w("joinHousehold: invite not found for code=$code")
            }
            user == null -> AppResult.Error("Sign in or continue in preview mode first.").also {
                Timber.w("joinHousehold: user not authenticated")
            }
            user.isPreview -> AppResult.Error("Cannot join household in preview mode.").also {
                Timber.w("joinHousehold failed: preview user attempted write operation")
            }
            else -> {
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
                syncRepository.syncPendingOperations()
                householdDao.getHousehold(invite.householdId)
                    ?.let { AppResult.Success(it.asModel()) }
                    ?: AppResult.Error("The household for that invite is no longer available.")
            }
        }
    }

    override suspend fun addMember(householdId: String, displayName: String): EmptyResult {
        Timber.d("addMember: householdId=$householdId displayName=$displayName")
        val user = currentUser()
        if (user?.isPreview == true) {
            return AppResult.Error("Cannot add members in preview mode").also {
                Timber.w("addMember failed: preview user attempted write operation")
            }
        }
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
        syncRepository.syncPendingOperations()
        return AppResult.Success(Unit)
    }

    override suspend fun createInvite(householdId: String): AppResult<Invite> {
        val user = currentUser()
        if (user?.isPreview == true) {
            return AppResult.Error("Cannot create invites in preview mode").also {
                Timber.w("createInvite failed: preview user attempted write operation")
            }
        }
        val invite = generateInvite(householdId)
        householdDao.updateInviteCode(householdId, invite.code)
        inviteDao.upsert(invite)
        enqueueOperation("invite", householdId, "upsert", invite.code)
        syncRepository.syncPendingOperations()
        return AppResult.Success(invite.asModel())
    }

    override suspend fun updateHouseholdName(householdId: String, name: String): EmptyResult {
        Timber.d("updateHouseholdName: householdId=$householdId name=$name")
        val user = currentUser()
        if (user?.isPreview == true) {
            return AppResult.Error("Cannot update household in preview mode").also {
                Timber.w("updateHouseholdName failed: preview user attempted write operation")
            }
        }
        val sanitizedName = name.trim().ifBlank { "My Household" }
        householdDao.updateName(householdId, sanitizedName)
        enqueueOperation("household", householdId, "rename", sanitizedName)
        syncRepository.syncPendingOperations()
        return AppResult.Success(Unit)
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
            code = UUID.randomUUID().toString().take(INVITE_CODE_LENGTH).uppercase(),
            createdAt = Clock.System.now(),
            consumedAt = null,
        )
}
