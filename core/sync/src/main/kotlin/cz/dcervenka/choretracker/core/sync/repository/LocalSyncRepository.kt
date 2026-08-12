package cz.dcervenka.choretracker.core.sync.repository

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.SyncRepository
import cz.dcervenka.choretracker.core.database.dao.ChoreDao
import cz.dcervenka.choretracker.core.database.dao.CompletionDao
import cz.dcervenka.choretracker.core.database.dao.CompletionParticipantDao
import cz.dcervenka.choretracker.core.database.dao.HouseholdDao
import cz.dcervenka.choretracker.core.database.dao.InviteDao
import cz.dcervenka.choretracker.core.database.dao.MemberDao
import cz.dcervenka.choretracker.core.database.dao.PendingSyncOperationDao
import cz.dcervenka.choretracker.core.database.dao.SyncStateDao
import cz.dcervenka.choretracker.core.database.database.ChoreTrackerDatabase
import cz.dcervenka.choretracker.core.database.entity.ChoreEntity
import cz.dcervenka.choretracker.core.database.entity.CompletionEntity
import cz.dcervenka.choretracker.core.database.entity.CompletionParticipantEntity
import cz.dcervenka.choretracker.core.database.entity.HouseholdEntity
import cz.dcervenka.choretracker.core.database.entity.InviteEntity
import cz.dcervenka.choretracker.core.database.entity.MemberEntity
import cz.dcervenka.choretracker.core.database.entity.PendingSyncOperationEntity
import cz.dcervenka.choretracker.core.database.entity.SyncStateEntity
import cz.dcervenka.choretracker.core.model.auth.AppUser
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.chore.ChoreCategory
import cz.dcervenka.choretracker.core.model.chore.ChoreCompletion
import cz.dcervenka.choretracker.core.model.household.Household
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import cz.dcervenka.choretracker.core.model.household.Invite
import cz.dcervenka.choretracker.core.model.sync.HouseholdSnapshot
import cz.dcervenka.choretracker.core.model.sync.SyncState
import cz.dcervenka.choretracker.core.remote.contract.RemoteHouseholdDataSource
import cz.dcervenka.choretracker.core.sync.di.SyncScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

@Suppress("TooManyFunctions", "LongParameterList")
@Singleton
class LocalSyncRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val householdDao: HouseholdDao,
    private val memberDao: MemberDao,
    private val choreDao: ChoreDao,
    private val completionDao: CompletionDao,
    private val completionParticipantDao: CompletionParticipantDao,
    private val inviteDao: InviteDao,
    private val pendingSyncOperationDao: PendingSyncOperationDao,
    private val syncStateDao: SyncStateDao,
    private val remoteHouseholdDataSource: RemoteHouseholdDataSource,
    private val database: ChoreTrackerDatabase,
    @SyncScope private val syncScope: CoroutineScope,
) : SyncRepository {

    private var emailSyncedThisSession = false

    init {
        authRepository.authState
            .flatMapLatest { authState ->
                val user = (authState as? AuthState.Authenticated)?.user
                if (user == null || user.isPreview) {
                    emptyFlow()
                } else {
                    householdDao.observeHouseholdForUser(user.id)
                        .map { it?.id }
                        .distinctUntilChanged()
                        .flatMapLatest { householdId ->
                            if (householdId == null) emptyFlow() else observeRealtimeUpdates(householdId, user.id)
                        }
                }
            }
            .launchIn(syncScope)
    }

    private fun observeRealtimeUpdates(householdId: String, userId: String): Flow<Unit> = merge(
        remoteHouseholdDataSource.observeMembers(householdId, userId)
            .onEach { applyRealtimeMembers(householdId, userId, it) },
        remoteHouseholdDataSource.observeCompletions(householdId)
            .onEach { applyRealtimeCompletions(householdId, it) },
        remoteHouseholdDataSource.observeInvites(householdId)
            .onEach { applyRealtimeInvites(householdId, it) },
        remoteHouseholdDataSource.observeChores(householdId)
            .onEach { applyRealtimeChores(it) },
    ).map { }

    private suspend fun applyRealtimeMembers(householdId: String, userId: String, members: List<HouseholdMember>) {
        if (members.none { it.userId == userId }) {
            Timber.w(
                "applyRealtimeMembers: userId=$userId no longer a member of household=$householdId - " +
                    "clearing local data",
            )
            database.clearAll()
            return
        }
        deduplicateMembers(members).forEach { member ->
            memberDao.upsert(
                MemberEntity(
                    id = member.id,
                    householdId = member.householdId,
                    userId = member.userId,
                    displayName = member.displayName,
                    role = member.role.name,
                    isCurrentUser = member.isCurrentUser,
                    email = member.email,
                ),
            )
        }
        val memberIds = members.map { it.id }.toSet()
        memberDao.getMembers(householdId)
            .filter { it.id !in memberIds }
            .forEach { memberDao.deleteById(it.id) }
    }

    private suspend fun applyRealtimeCompletions(householdId: String, completions: List<ChoreCompletion>) {
        completions.forEach { completion ->
            completionDao.upsert(
                CompletionEntity(
                    id = completion.id,
                    householdId = completion.householdId,
                    choreId = completion.choreId,
                    createdAt = completion.createdAt,
                    createdByUserId = completion.createdByUserId,
                    note = completion.note,
                ),
            )
            completionParticipantDao.deleteByCompletionId(completion.id)
            completionParticipantDao.insertAll(
                completion.participantMemberIds.map { memberId ->
                    CompletionParticipantEntity(
                        completionId = completion.id,
                        memberId = memberId,
                    )
                },
            )
        }
        val completionIds = completions.map { it.id }.toSet()
        completionDao.getCompletions(householdId)
            .filter { it.id !in completionIds }
            .forEach { completionDao.deleteById(it.id) }
    }

    private suspend fun applyRealtimeInvites(householdId: String, invites: List<Invite>) {
        invites.forEach { invite ->
            inviteDao.upsert(
                InviteEntity(
                    id = invite.id,
                    householdId = invite.householdId,
                    code = invite.code,
                    createdAt = invite.createdAt,
                    consumedAt = invite.consumedAt,
                    targetMemberId = invite.targetMemberId,
                    consumedByMemberId = invite.consumedByMemberId,
                ),
            )
        }
        val inviteIds = invites.map { it.id }.toSet()
        inviteDao.getInvites(householdId)
            .filter { it.id !in inviteIds }
            .forEach { inviteDao.deleteById(it.id) }
    }

    // No prune step: chores are soft-deleted (isActive/deletedAt fields, see ChoreEntity), never
    // removed from Firestore - same as the existing pull-based sync (pruneStaleLocalRows doesn't
    // touch chores either), so there's nothing stale to reconcile here.
    private suspend fun applyRealtimeChores(chores: List<Chore>) {
        chores.forEach { chore ->
            choreDao.upsert(
                ChoreEntity(
                    id = chore.id,
                    householdId = chore.householdId,
                    name = chore.name,
                    isActive = chore.isActive,
                    createdAt = chore.createdAt,
                    deletedAt = chore.deletedAt,
                    frequencyDays = chore.frequencyDays,
                    category = chore.category.name,
                ),
            )
        }
    }

    override fun observeSyncState(householdId: String): Flow<SyncState?> =
        syncStateDao.observeSyncState(householdId).map { state ->
            state?.let {
                SyncState(
                    householdId = it.householdId,
                    lastSyncedAt = it.lastSyncedAt,
                    lastSyncAttemptAt = it.lastSyncAttemptAt,
                    pendingOperations = it.pendingOperations,
                    lastErrorMessage = it.lastErrorMessage,
                )
            }
        }

    // Runs detached from the caller's own coroutine scope (syncScope is a long-lived singleton,
    // see SyncModule) so that a UI-triggered call - e.g. a screen popping its own back stack
    // right after dispatching a mutation - can't cut the remote push short. The caller still
    // suspends until the push finishes; it just can no longer cancel it out from under itself.
    override suspend fun syncPendingOperations(): EmptyResult = syncScope.async {
        val authenticatedUser = (authRepository.authState.first() as? AuthState.Authenticated)?.user
        val shouldSkipSync = authenticatedUser == null || authenticatedUser.isPreview
        if (shouldSkipSync) {
            return@async AppResult.Success(Unit)
        }

        val currentEmail = authenticatedUser.email
        if (!emailSyncedThisSession && currentEmail != null) {
            emailSyncedThisSession = true
            ensureEmailSynced(authenticatedUser.id, currentEmail)
        }

        val operations = pendingSyncOperationDao.getAll()
        Timber.d("syncPendingOperations: ${operations.size} pending operations")
        val operationIdsByHouseholdId = linkedMapOf<String, MutableList<String>>().apply {
            operations.forEach { operation ->
                resolveHouseholdId(operation)?.let { householdId ->
                    getOrPut(householdId) { mutableListOf() }.add(operation.id)
                }
            }
        }

        val syncError = if (operations.isEmpty()) {
            null
        } else {
            operationIdsByHouseholdId.entries.firstNotNullOfOrNull { (householdId, operationIds) ->
                syncHousehold(householdId, operationIds, operations, authenticatedUser)
            }
        }

        syncError ?: AppResult.Success(Unit)
    }.await()

    override suspend fun restoreHouseholdForUser(userId: String): AppResult<Boolean> {
        Timber.d("restoreHouseholdForUser: userId=$userId")
        return when (val result = remoteHouseholdDataSource.fetchHouseholdSnapshot(userId)) {
            is AppResult.Error -> {
                Timber.e("restoreHouseholdForUser: fetch failed - ${result.message}")
                AppResult.Error(result.message, result.cause)
            }
            is AppResult.Success -> {
                val snapshot = result.value
                when {
                    snapshot == null -> AppResult.Success(false).also {
                        Timber.d("restoreHouseholdForUser: no remote snapshot found")
                    }
                    snapshot.members.none { it.userId == userId } &&
                        householdDao.getCurrentHouseholdForUser(userId) != null -> {
                        Timber.w(
                            "restoreHouseholdForUser: userId=$userId no longer a member of " +
                                "household=${snapshot.household.id} - clearing local data",
                        )
                        database.clearAll()
                        AppResult.Success(false)
                    }
                    else -> applySnapshot(snapshot)
                }
            }
        }
    }

    private suspend fun applySnapshot(snapshot: HouseholdSnapshot): AppResult<Boolean> {
        householdDao.upsert(
            HouseholdEntity(
                id = snapshot.household.id,
                name = snapshot.household.name,
                ownerUserId = snapshot.household.ownerUserId,
                inviteCode = snapshot.household.inviteCode,
                createdAt = snapshot.household.createdAt,
            ),
        )
        deduplicateMembers(snapshot.members).forEach { member ->
            memberDao.upsert(
                MemberEntity(
                    id = member.id,
                    householdId = member.householdId,
                    userId = member.userId,
                    displayName = member.displayName,
                    role = member.role.name,
                    isCurrentUser = member.isCurrentUser,
                    email = member.email,
                ),
            )
        }
        snapshot.chores.forEach { chore ->
            choreDao.upsert(
                ChoreEntity(
                    id = chore.id,
                    householdId = chore.householdId,
                    name = chore.name,
                    isActive = chore.isActive,
                    createdAt = chore.createdAt,
                    deletedAt = chore.deletedAt,
                    frequencyDays = chore.frequencyDays,
                    category = chore.category.name,
                ),
            )
        }
        snapshot.completions.forEach { completion ->
            completionDao.upsert(
                CompletionEntity(
                    id = completion.id,
                    householdId = completion.householdId,
                    choreId = completion.choreId,
                    createdAt = completion.createdAt,
                    createdByUserId = completion.createdByUserId,
                    note = completion.note,
                ),
            )
            completionParticipantDao.insertAll(
                completion.participantMemberIds.map { memberId ->
                    CompletionParticipantEntity(
                        completionId = completion.id,
                        memberId = memberId,
                    )
                },
            )
        }
        snapshot.invites.forEach { invite ->
            inviteDao.upsert(
                InviteEntity(
                    id = invite.id,
                    householdId = invite.householdId,
                    code = invite.code,
                    createdAt = invite.createdAt,
                    consumedAt = invite.consumedAt,
                    targetMemberId = invite.targetMemberId,
                    consumedByMemberId = invite.consumedByMemberId,
                ),
            )
        }
        pruneStaleLocalRows(snapshot)
        Timber.d(
            "restoreHouseholdForUser: restored household=${snapshot.household.id} " +
                "members=${snapshot.members.size} chores=${snapshot.chores.size} completions=${snapshot.completions.size}",
        )
        return AppResult.Success(true)
    }

    private suspend fun syncHousehold(
        householdId: String,
        operationIds: List<String>,
        operations: List<PendingSyncOperationEntity>,
        authenticatedUser: AppUser,
    ): AppResult.Error? {
        val now = Clock.System.now()
        val isOwner = householdDao.getHousehold(householdId)?.ownerUserId == authenticatedUser.id
        val result = performRemoteSync(householdId, isOwner, authenticatedUser) ?: return null
        return when (result) {
            is AppResult.Error -> {
                Timber.e(
                    result.cause,
                    "syncPendingOperations: sync failed for household=$householdId - ${result.message}",
                )
                syncStateDao.upsert(
                    SyncStateEntity(
                        householdId = householdId,
                        lastSyncedAt = null,
                        lastSyncAttemptAt = now,
                        pendingOperations = operationIds.size,
                        lastErrorMessage = result.message,
                    ),
                )
                AppResult.Error(result.message, result.cause)
            }
            is AppResult.Success -> {
                Timber.d("syncPendingOperations: synced ${operationIds.size} operations for household=$householdId")
                if (isOwner) deleteRemoteMembers(householdId, operations, operationIds.toSet())
                deleteRemoteCompletions(householdId, operations, operationIds.toSet())
                consumeRemoteInvites(householdId, operations, operationIds.toSet())
                operationIds.forEach { pendingSyncOperationDao.delete(it) }
                syncStateDao.upsert(
                    SyncStateEntity(
                        householdId = householdId,
                        lastSyncedAt = now,
                        lastSyncAttemptAt = now,
                        pendingOperations = 0,
                        lastErrorMessage = null,
                    ),
                )
                null
            }
        }
    }

    private suspend fun performRemoteSync(
        householdId: String,
        isOwner: Boolean,
        authenticatedUser: AppUser,
    ): EmptyResult? = if (isOwner) {
        performOwnerSync(householdId, authenticatedUser)
    } else {
        performMemberSync(householdId, authenticatedUser)
    }

    private suspend fun performOwnerSync(householdId: String, authenticatedUser: AppUser): EmptyResult? {
        val snapshot = buildSnapshot(householdId, authenticatedUser.id, authenticatedUser.email)
            ?: return null
        return remoteHouseholdDataSource.upsertHouseholdSnapshot(snapshot, authenticatedUser.id)
    }

    private suspend fun performMemberSync(householdId: String, authenticatedUser: AppUser): EmptyResult? {
        val (member, completions) = buildMemberSync(householdId, authenticatedUser.id) ?: return null
        return remoteHouseholdDataSource.upsertMemberSnapshot(
            householdId = householdId,
            member = member.copy(email = authenticatedUser.email),
            completions = completions,
            userId = authenticatedUser.id,
        )
    }

    private suspend fun ensureEmailSynced(userId: String, email: String) {
        val householdId = householdDao.getCurrentHouseholdForUser(userId)?.id ?: return
        val (member, _) = buildMemberSync(householdId, userId) ?: return
        remoteHouseholdDataSource.upsertMemberSnapshot(
            householdId = householdId,
            member = member.copy(email = email),
            completions = emptyList(),
            userId = userId,
        )
        Timber.d("ensureEmailSynced: wrote email for userId=$userId")
    }

    private suspend fun deleteRemoteMembers(
        householdId: String,
        operations: List<PendingSyncOperationEntity>,
        operationIdSet: Set<String>,
    ) {
        operations
            .filter { it.id in operationIdSet && it.entityType == "member" && it.operationType == "delete" }
            .forEach { op ->
                val result = remoteHouseholdDataSource.deleteMember(householdId, op.payload)
                if (result is AppResult.Error) {
                    Timber.e(
                        "syncPendingOperations: remote member delete failed for ${op.payload} — ${result.message}",
                    )
                }
            }
    }

    private suspend fun deleteRemoteCompletions(
        householdId: String,
        operations: List<PendingSyncOperationEntity>,
        operationIdSet: Set<String>,
    ) {
        operations
            .filter { it.id in operationIdSet && it.entityType == "completion" && it.operationType == "delete" }
            .forEach { op ->
                val result = remoteHouseholdDataSource.deleteCompletion(householdId, op.entityId)
                if (result is AppResult.Error) {
                    Timber.e(
                        "syncPendingOperations: remote completion delete failed for ${op.entityId} — ${result.message}",
                    )
                }
            }
    }

    override suspend fun ensureInviteLocal(code: String): EmptyResult {
        val result = remoteHouseholdDataSource.fetchInviteByCode(code)
        if (result is AppResult.Error) return result
        val invite = (result as AppResult.Success).value
        return if (invite == null) {
            AppResult.Error("No invite with that code was found.")
        } else {
            inviteDao.upsert(
                InviteEntity(
                    id = invite.id,
                    householdId = invite.householdId,
                    code = invite.code,
                    createdAt = invite.createdAt,
                    consumedAt = invite.consumedAt,
                    targetMemberId = invite.targetMemberId,
                    consumedByMemberId = invite.consumedByMemberId,
                ),
            )
            AppResult.Success(Unit)
        }
    }

    private fun deduplicateMembers(members: List<HouseholdMember>): List<HouseholdMember> =
        members.groupBy { it.id }.values.map { group ->
            val claimed = group.firstOrNull { it.userId != null }
            val placeholder = group.firstOrNull { it.userId == null }
            when {
                claimed != null && placeholder != null ->
                    claimed.copy(displayName = placeholder.displayName.ifBlank { claimed.displayName })
                else -> group.maxByOrNull { if (it.userId != null) 1 else 0 }!!
            }
        }

    private suspend fun pruneStaleLocalRows(snapshot: HouseholdSnapshot) {
        val householdId = snapshot.household.id
        val memberIds = snapshot.members.map { it.id }.toSet()
        memberDao.getMembers(householdId)
            .filter { it.id !in memberIds }
            .forEach { memberDao.deleteById(it.id) }
        val inviteIds = snapshot.invites.map { it.id }.toSet()
        inviteDao.getInvites(householdId)
            .filter { it.id !in inviteIds }
            .forEach { inviteDao.deleteById(it.id) }
        val completionIds = snapshot.completions.map { it.id }.toSet()
        completionDao.getCompletions(householdId)
            .filter { it.id !in completionIds }
            .forEach { completionDao.deleteById(it.id) }
    }

    private suspend fun consumeRemoteInvites(
        householdId: String,
        operations: List<PendingSyncOperationEntity>,
        operationIdSet: Set<String>,
    ) {
        operations
            .filter { it.id in operationIdSet && it.entityType == "invite" && it.operationType == "consumed" }
            .forEach { op ->
                val invite = inviteDao.getInvites(householdId).find { it.id == op.payload } ?: return@forEach
                val consumedAt = invite.consumedAt ?: return@forEach
                val consumedByMemberId = invite.consumedByMemberId ?: return@forEach
                val result = remoteHouseholdDataSource.markInviteConsumed(
                    householdId,
                    op.payload,
                    consumedAt,
                    consumedByMemberId,
                )
                if (result is AppResult.Error) {
                    Timber.e(
                        "syncPendingOperations: remote invite consumed failed for ${op.payload} — ${result.message}",
                    )
                }
            }
    }

    private suspend fun resolveHouseholdId(
        operation: PendingSyncOperationEntity,
    ): String? = when (operation.entityType) {
        "household", "member", "invite" -> operation.entityId
        "chore" -> choreDao.getChore(operation.entityId)?.householdId
        "completion" -> if (operation.operationType == "delete") {
            operation.payload.takeIf { it.isNotBlank() }
        } else {
            completionDao.getCompletion(operation.entityId)?.householdId
        }
        else -> null
    }

    private suspend fun buildSnapshot(
        householdId: String,
        currentUserId: String,
        currentUserEmail: String?,
    ): HouseholdSnapshot? {
        val household = householdDao.getHousehold(householdId) ?: return null
        val participants = completionParticipantDao.getParticipants(householdId)
        val members = memberDao.getMembers(householdId).map { member ->
            HouseholdMember(
                id = member.id,
                householdId = member.householdId,
                userId = member.userId,
                displayName = member.displayName,
                role = runCatching { HouseholdRole.valueOf(member.role) }.getOrDefault(HouseholdRole.MEMBER),
                isCurrentUser = member.isCurrentUser,
                email = if (member.userId == currentUserId) currentUserEmail else member.email,
            )
        }
        val completions = completionDao.getCompletions(householdId).map { completion ->
            ChoreCompletion(
                id = completion.id,
                householdId = completion.householdId,
                choreId = completion.choreId,
                createdAt = completion.createdAt,
                createdByUserId = completion.createdByUserId,
                note = completion.note,
                participantMemberIds = participants
                    .filter { it.completionId == completion.id }
                    .map { it.memberId },
            )
        }
        return HouseholdSnapshot(
            household = Household(
                id = household.id,
                name = household.name,
                ownerUserId = household.ownerUserId,
                inviteCode = household.inviteCode,
                createdAt = household.createdAt,
            ),
            members = members,
            chores = choreDao.getChores(householdId).map { chore ->
                Chore(
                    id = chore.id,
                    householdId = chore.householdId,
                    name = chore.name,
                    isActive = chore.isActive,
                    createdAt = chore.createdAt,
                    deletedAt = chore.deletedAt,
                    frequencyDays = chore.frequencyDays,
                    category = runCatching { ChoreCategory.valueOf(chore.category) }.getOrDefault(ChoreCategory.OTHER),
                )
            },
            completions = completions,
            invites = inviteDao.getInvites(householdId).map { invite ->
                Invite(
                    id = invite.id,
                    householdId = invite.householdId,
                    code = invite.code,
                    createdAt = invite.createdAt,
                    consumedAt = invite.consumedAt,
                    targetMemberId = invite.targetMemberId,
                    consumedByMemberId = invite.consumedByMemberId,
                )
            },
        )
    }

    private suspend fun buildMemberSync(
        householdId: String,
        userId: String,
    ): Pair<HouseholdMember, List<ChoreCompletion>>? {
        val memberEntity = memberDao.findByUserId(householdId, userId) ?: return null
        val participants = completionParticipantDao.getParticipants(householdId)
        val ownCompletions = completionDao.getCompletions(householdId)
            .filter { it.createdByUserId == userId }
            .map { completion ->
                ChoreCompletion(
                    id = completion.id,
                    householdId = completion.householdId,
                    choreId = completion.choreId,
                    createdAt = completion.createdAt,
                    createdByUserId = completion.createdByUserId,
                    note = completion.note,
                    participantMemberIds = participants
                        .filter { it.completionId == completion.id }
                        .map { it.memberId },
                )
            }
        val member = HouseholdMember(
            id = memberEntity.id,
            householdId = memberEntity.householdId,
            userId = memberEntity.userId,
            displayName = memberEntity.displayName,
            role = runCatching { HouseholdRole.valueOf(memberEntity.role) }.getOrDefault(HouseholdRole.MEMBER),
            isCurrentUser = memberEntity.isCurrentUser,
        )
        return member to ownCompletions
    }
}
