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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

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

    private val realtimeSyncApplier = RealtimeSyncApplier(
        memberDao = memberDao,
        choreDao = choreDao,
        completionDao = completionDao,
        completionParticipantDao = completionParticipantDao,
        inviteDao = inviteDao,
        pendingSyncOperationDao = pendingSyncOperationDao,
        database = database,
    )

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
            // retry, not catch: an unexpected exception here (e.g. a malformed document a
            // future change to asMember/asChore/etc. lets through) would otherwise permanently
            // end this subscription with only a log line - every real-time update after that
            // point would be silently missed for the rest of the process's lifetime. Mirrors
            // FcmTokenRegistrar's identical reasoning for the same shape of subscription.
            .retry { error ->
                Timber.e(error, "LocalSyncRepository: real-time sync subscription failed, retrying")
                delay(5.seconds)
                true
            }
            .launchIn(syncScope)
    }

    private fun observeRealtimeUpdates(householdId: String, userId: String): Flow<Unit> = merge(
        remoteHouseholdDataSource.observeMembers(householdId, userId)
            .onEach { realtimeSyncApplier.applyMembers(householdId, userId, it) },
        remoteHouseholdDataSource.observeCompletions(householdId)
            .onEach { realtimeSyncApplier.applyCompletions(householdId, it) },
        remoteHouseholdDataSource.observeInvites(householdId)
            .onEach { realtimeSyncApplier.applyInvites(householdId, it) },
        remoteHouseholdDataSource.observeChores(householdId)
            .onEach { realtimeSyncApplier.applyChores(it) },
    ).map { }

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
                    snapshot.members.none { it.userId == userId && it.removedAt == null } -> {
                        // Not an active member of the fetched household (removed, or left). Never
                        // applySnapshot here - the fetch returns an empty-members sentinel with a
                        // blank name/ownerUserId on PERMISSION_DENIED, and writing that into Room
                        // corrupts local state. Wipe local data if any is still present.
                        Timber.w(
                            "restoreHouseholdForUser: userId=$userId is not an active member of " +
                                "household=${snapshot.household.id}",
                        )
                        if (householdDao.getCurrentHouseholdForUser(userId) != null) {
                            database.clearAll()
                        }
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
                    joinedViaInviteId = member.joinedViaInviteId,
                    removedAt = member.removedAt,
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
        val result = performRemoteSync(householdId, isOwner, authenticatedUser)
        if (result == null) {
            // buildSnapshot/buildMemberSync found no local household or member row to sync from -
            // these operations can never succeed from this state, so discard them instead of
            // leaving them queued forever and retried on every future sync with no way to
            // resolve and no error ever surfaced to the user.
            Timber.w(
                "syncHousehold: no local data to sync for household=$householdId - discarding " +
                    "${operationIds.size} orphaned pending operation(s)",
            )
            operationIds.forEach { pendingSyncOperationDao.delete(it) }
            return null
        }
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
                val operationIdSet = operationIds.toSet()
                val failedOperationIds = buildSet {
                    // Member removal has no dedicated remote call - it's a soft removedAt/active
                    // field write that rides the full-snapshot push in performRemoteSync above,
                    // same as a chore's deletedAt.
                    addAll(deleteRemoteCompletions(householdId, operations, operationIdSet))
                    addAll(consumeRemoteInvites(householdId, operations, operationIdSet))
                }
                // Only operations that actually landed remotely are cleared from the queue -
                // anything that failed (e.g. a delete/consume Firestore rejected) stays pending
                // and is retried on the next sync, instead of being silently dropped.
                operationIds.filterNot { it in failedOperationIds }.forEach { pendingSyncOperationDao.delete(it) }
                val errorMessage = if (failedOperationIds.isEmpty()) {
                    null
                } else {
                    "${failedOperationIds.size} change(s) couldn't be applied remotely and will retry."
                }
                syncStateDao.upsert(
                    SyncStateEntity(
                        householdId = householdId,
                        lastSyncedAt = now,
                        lastSyncAttemptAt = now,
                        pendingOperations = failedOperationIds.size,
                        lastErrorMessage = errorMessage,
                    ),
                )
                errorMessage?.let { AppResult.Error(it) }
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

    private suspend fun deleteRemoteCompletions(
        householdId: String,
        operations: List<PendingSyncOperationEntity>,
        operationIdSet: Set<String>,
    ): Set<String> {
        val failedOperationIds = mutableSetOf<String>()
        operations
            .filter { it.id in operationIdSet && it.entityType == "completion" && it.operationType == "delete" }
            .forEach { op ->
                val result = remoteHouseholdDataSource.deleteCompletion(householdId, op.entityId)
                if (result is AppResult.Error) {
                    Timber.e(
                        "syncPendingOperations: remote completion delete failed for ${op.entityId} — ${result.message}",
                    )
                    failedOperationIds += op.id
                }
            }
        return failedOperationIds
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

    override suspend fun transferOwnership(
        householdId: String,
        newOwnerUserId: String,
        newOwnerMemberDocId: String,
        previousOwnerMemberDocId: String,
    ): EmptyResult = remoteHouseholdDataSource.transferOwnership(
        householdId = householdId,
        newOwnerUserId = newOwnerUserId,
        newOwnerMemberDocId = newOwnerMemberDocId,
        previousOwnerMemberDocId = previousOwnerMemberDocId,
    )

    override suspend fun leaveHousehold(householdId: String, selfMemberDocId: String): EmptyResult {
        return when (val result = remoteHouseholdDataSource.leaveHousehold(householdId, selfMemberDocId)) {
            is AppResult.Error -> result
            is AppResult.Success -> {
                // Same wipe-and-redirect path a removed member takes (PR #80): clearing the
                // tables makes observeHouseholdForUser emit null -> ObserveStartupDestinationUseCase
                // -> ONBOARDING.
                database.clearAll()
                AppResult.Success(Unit)
            }
        }
    }

    // A row absent from the pulled snapshot might just not have synced yet, not actually be
    // deleted remotely - pruning it here would discard local data that a pending operation is
    // still waiting to push. member/invite pending operations are keyed by householdId (not
    // their own id - see enqueueOperation call sites), so those two are skipped as a whole
    // for a household with any pending operation of that type; completions are enqueued with
    // their own id as entityId, so those are excluded from pruning individually.
    private suspend fun pruneStaleLocalRows(snapshot: HouseholdSnapshot) {
        val householdId = snapshot.household.id
        val pendingOperations = pendingSyncOperationDao.getAll()

        val memberIds = snapshot.members.map { it.id }.toSet()
        val hasPendingMemberOps = pendingOperations.any { it.entityType == "member" && it.entityId == householdId }
        if (!hasPendingMemberOps) {
            memberDao.getMembers(householdId)
                .filter { it.id !in memberIds }
                .forEach { memberDao.deleteById(it.id) }
        }

        val inviteIds = snapshot.invites.map { it.id }.toSet()
        val hasPendingInviteOps = pendingOperations.any { it.entityType == "invite" && it.entityId == householdId }
        if (!hasPendingInviteOps) {
            inviteDao.getInvites(householdId)
                .filter { it.id !in inviteIds }
                .forEach { inviteDao.deleteById(it.id) }
        }

        val completionIds = snapshot.completions.map { it.id }.toSet()
        val pendingCompletionIds = pendingOperations
            .filter { it.entityType == "completion" }
            .map { it.entityId }
            .toSet()
        completionDao.getCompletions(householdId)
            .filter { it.id !in completionIds && it.id !in pendingCompletionIds }
            .forEach { completionDao.deleteById(it.id) }
    }

    private suspend fun consumeRemoteInvites(
        householdId: String,
        operations: List<PendingSyncOperationEntity>,
        operationIdSet: Set<String>,
    ): Set<String> {
        val failedOperationIds = mutableSetOf<String>()
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
                    failedOperationIds += op.id
                }
            }
        return failedOperationIds
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
                joinedViaInviteId = member.joinedViaInviteId,
                // Load-bearing: the owner's full-snapshot push writes active = removedAt == null
                // for every member on every sync - dropping this here would silently re-activate
                // a removed member on the next unrelated owner sync.
                removedAt = member.removedAt,
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
            joinedViaInviteId = memberEntity.joinedViaInviteId,
            removedAt = memberEntity.removedAt,
        )
        return member to ownCompletions
    }
}
