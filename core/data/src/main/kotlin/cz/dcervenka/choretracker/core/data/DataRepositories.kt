package cz.dcervenka.choretracker.core.data

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.ChoreCompletionRepository
import cz.dcervenka.choretracker.core.data.contract.ChoreRepository
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.data.contract.StatsRepository
import cz.dcervenka.choretracker.core.database.ChoreDao
import cz.dcervenka.choretracker.core.database.ChoreEntity
import cz.dcervenka.choretracker.core.database.CompletionDao
import cz.dcervenka.choretracker.core.database.CompletionEntity
import cz.dcervenka.choretracker.core.database.CompletionParticipantDao
import cz.dcervenka.choretracker.core.database.CompletionParticipantEntity
import cz.dcervenka.choretracker.core.database.HouseholdDao
import cz.dcervenka.choretracker.core.database.HouseholdEntity
import cz.dcervenka.choretracker.core.database.InviteDao
import cz.dcervenka.choretracker.core.database.InviteEntity
import cz.dcervenka.choretracker.core.database.MemberDao
import cz.dcervenka.choretracker.core.database.MemberEntity
import cz.dcervenka.choretracker.core.database.PendingSyncOperationDao
import cz.dcervenka.choretracker.core.database.PendingSyncOperationEntity
import cz.dcervenka.choretracker.core.model.AppUser
import cz.dcervenka.choretracker.core.model.AuthState
import cz.dcervenka.choretracker.core.model.Chore
import cz.dcervenka.choretracker.core.model.ChoreCompletion
import cz.dcervenka.choretracker.core.model.ChoreStaleness
import cz.dcervenka.choretracker.core.model.DashboardSnapshot
import cz.dcervenka.choretracker.core.model.Household
import cz.dcervenka.choretracker.core.model.HouseholdMember
import cz.dcervenka.choretracker.core.model.HouseholdRole
import cz.dcervenka.choretracker.core.model.Invite
import cz.dcervenka.choretracker.core.model.RecentCompletion
import cz.dcervenka.choretracker.core.model.StatsSnapshot
import cz.dcervenka.choretracker.core.domain.HouseholdStatisticsCalculator
import cz.dcervenka.choretracker.core.remote.contract.RemoteAuthDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

@Singleton
class PreviewAwareAuthRepository @Inject constructor(
    private val remoteAuthDataSource: RemoteAuthDataSource,
) : AuthRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val previewState = MutableStateFlow<AuthState?>(null)

    override val authState: Flow<AuthState> = combine(
        remoteAuthDataSource.authState,
        previewState,
    ) { remoteState, preview ->
        preview ?: remoteState
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = if (remoteAuthDataSource.isConfigured) AuthState.SignedOut else AuthState.RequiresConfiguration,
    )

    override suspend fun signIn(email: String, password: String): EmptyResult =
        remoteAuthDataSource.signIn(email, password)

    override suspend fun signUp(email: String, password: String, displayName: String): EmptyResult =
        remoteAuthDataSource.signUp(email, password, displayName)

    override suspend fun continueInPreviewMode(displayName: String): EmptyResult {
        previewState.value = AuthState.Authenticated(
            AppUser(
                id = "preview-user",
                email = null,
                displayName = displayName.ifBlank { "Preview User" },
                isPreview = true,
            ),
        )
        return AppResult.Success(Unit)
    }

    override suspend fun signOut(): EmptyResult {
        previewState.value = null
        return remoteAuthDataSource.signOut()
    }
}

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

@Singleton
class OfflineFirstChoreRepository @Inject constructor(
    private val choreDao: ChoreDao,
    private val pendingSyncOperationDao: PendingSyncOperationDao,
) : ChoreRepository {
    override fun observeChores(householdId: String): Flow<List<Chore>> =
        choreDao.observeChores(householdId).map { chores -> chores.map(ChoreEntity::asModel) }

    override suspend fun addChore(householdId: String, name: String): EmptyResult {
        val choreId = UUID.randomUUID().toString()
        choreDao.upsert(
            ChoreEntity(
                id = choreId,
                householdId = householdId,
                name = name,
                isActive = true,
                createdAt = Clock.System.now(),
                deletedAt = null,
            ),
        )
        pendingSyncOperationDao.upsert(
            PendingSyncOperationEntity(
                id = UUID.randomUUID().toString(),
                entityType = "chore",
                entityId = choreId,
                operationType = "upsert",
                payload = name,
                createdAt = Clock.System.now(),
            ),
        )
        return AppResult.Success(Unit)
    }

    override suspend fun updateChoreActive(choreId: String, isActive: Boolean): EmptyResult {
        choreDao.updateActive(choreId, isActive, Clock.System.now())
        pendingSyncOperationDao.upsert(
            PendingSyncOperationEntity(
                id = UUID.randomUUID().toString(),
                entityType = "chore",
                entityId = choreId,
                operationType = if (isActive) "reactivate" else "deactivate",
                payload = isActive.toString(),
                createdAt = Clock.System.now(),
            ),
        )
        return AppResult.Success(Unit)
    }
}

@Singleton
class OfflineFirstChoreCompletionRepository @Inject constructor(
    private val completionDao: CompletionDao,
    private val participantDao: CompletionParticipantDao,
    private val choreDao: ChoreDao,
    private val memberDao: MemberDao,
    private val pendingSyncOperationDao: PendingSyncOperationDao,
    private val authRepository: AuthRepository,
) : ChoreCompletionRepository {

    override fun observeRecentCompletions(householdId: String, limit: Int): Flow<List<RecentCompletion>> =
        combine(
            completionDao.observeCompletions(householdId),
            choreDao.observeChores(householdId),
            memberDao.observeMembers(householdId),
            participantDao.observeParticipants(householdId),
        ) { completions, chores, members, participants ->
            val choreMap = chores.associateBy { it.id }
            val memberMap = members.associateBy { it.id }
            completions.take(limit).map { completion ->
                val participantNames = participants.filter { it.completionId == completion.id }
                    .mapNotNull { participant -> memberMap[participant.memberId]?.displayName }
                RecentCompletion(
                    completionId = completion.id,
                    choreName = choreMap[completion.choreId]?.name.orEmpty(),
                    note = completion.note,
                    completedAt = completion.createdAt,
                    participantNames = participantNames,
                )
            }
        }

    override suspend fun logCompletion(
        householdId: String,
        choreId: String,
        participantMemberIds: List<String>,
        note: String?,
    ): EmptyResult {
        val currentUserId = (authRepository.authState.first() as? AuthState.Authenticated)?.user?.id
            ?: return AppResult.Error("Sign in or continue in preview mode first.")
        val completionId = UUID.randomUUID().toString()
        completionDao.upsert(
            CompletionEntity(
                id = completionId,
                householdId = householdId,
                choreId = choreId,
                createdAt = Clock.System.now(),
                createdByUserId = currentUserId,
                note = note?.takeIf(String::isNotBlank),
            ),
        )
        participantDao.insertAll(
            participantMemberIds.distinct().map { memberId ->
                CompletionParticipantEntity(
                    completionId = completionId,
                    memberId = memberId,
                )
            },
        )
        pendingSyncOperationDao.upsert(
            PendingSyncOperationEntity(
                id = UUID.randomUUID().toString(),
                entityType = "completion",
                entityId = completionId,
                operationType = "upsert",
                payload = note.orEmpty(),
                createdAt = Clock.System.now(),
            ),
        )
        return AppResult.Success(Unit)
    }
}

@Singleton
class OfflineFirstStatsRepository @Inject constructor(
    private val householdDao: HouseholdDao,
    private val memberDao: MemberDao,
    private val choreDao: ChoreDao,
    private val completionDao: CompletionDao,
    private val participantDao: CompletionParticipantDao,
    private val statisticsCalculator: HouseholdStatisticsCalculator,
) : StatsRepository {

    override fun observeDashboard(householdId: String): Flow<DashboardSnapshot> =
        combine(
            householdDao.observeHousehold(householdId),
            memberDao.observeMembers(householdId),
            choreDao.observeChores(householdId),
            completionDao.observeCompletions(householdId),
            participantDao.observeParticipants(householdId),
        ) { household, members, chores, completions, participants ->
            val safeHousehold = household?.asModel()
                ?: Household(
                    id = householdId,
                    name = "Household",
                    ownerUserId = "",
                    inviteCode = "",
                    createdAt = Clock.System.now(),
                )
            statisticsCalculator.dashboardSnapshot(
                household = safeHousehold,
                members = members.map(MemberEntity::asModel),
                chores = chores.map(ChoreEntity::asModel),
                completions = completions.asModels(participants),
                today = Clock.System.todayIn(TimeZone.currentSystemDefault()),
            )
        }

    override fun observeStats(householdId: String): Flow<StatsSnapshot> =
        combine(
            householdDao.observeHousehold(householdId),
            memberDao.observeMembers(householdId),
            choreDao.observeChores(householdId),
            completionDao.observeCompletions(householdId),
            participantDao.observeParticipants(householdId),
        ) { household, members, chores, completions, participants ->
            val safeHousehold = household?.asModel()
                ?: Household(
                    id = householdId,
                    name = "Household",
                    ownerUserId = "",
                    inviteCode = "",
                    createdAt = Clock.System.now(),
                )
            statisticsCalculator.statsSnapshot(
                household = safeHousehold,
                members = members.map(MemberEntity::asModel),
                chores = chores.map(ChoreEntity::asModel),
                completions = completions.asModels(participants),
                today = Clock.System.todayIn(TimeZone.currentSystemDefault()),
            )
        }
}

private fun HouseholdEntity.asModel(): Household = Household(
    id = id,
    name = name,
    ownerUserId = ownerUserId,
    inviteCode = inviteCode,
    createdAt = createdAt,
)

private fun MemberEntity.asModel(): HouseholdMember = HouseholdMember(
    id = id,
    householdId = householdId,
    userId = userId,
    displayName = displayName,
    role = HouseholdRole.valueOf(role),
    isCurrentUser = isCurrentUser,
)

private fun ChoreEntity.asModel(): Chore = Chore(
    id = id,
    householdId = householdId,
    name = name,
    isActive = isActive,
    createdAt = createdAt,
    deletedAt = deletedAt,
)

private fun InviteEntity.asModel(): Invite = Invite(
    id = id,
    householdId = householdId,
    code = code,
    createdAt = createdAt,
    consumedAt = consumedAt,
)

private fun List<CompletionEntity>.asModels(
    participants: List<CompletionParticipantEntity>,
): List<ChoreCompletion> {
    val participantIdsByCompletionId = participants.groupBy(CompletionParticipantEntity::completionId)
        .mapValues { (_, completionParticipants) ->
            completionParticipants.map(CompletionParticipantEntity::memberId)
        }
    return map { completion ->
        ChoreCompletion(
            id = completion.id,
            householdId = completion.householdId,
            choreId = completion.choreId,
            createdAt = completion.createdAt,
            createdByUserId = completion.createdByUserId,
            note = completion.note,
            participantMemberIds = participantIdsByCompletionId[completion.id].orEmpty(),
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun bindAuthRepository(impl: PreviewAwareAuthRepository): AuthRepository

    @Binds
    abstract fun bindHouseholdRepository(impl: OfflineFirstHouseholdRepository): HouseholdRepository

    @Binds
    abstract fun bindChoreRepository(impl: OfflineFirstChoreRepository): ChoreRepository

    @Binds
    abstract fun bindChoreCompletionRepository(
        impl: OfflineFirstChoreCompletionRepository,
    ): ChoreCompletionRepository

    @Binds
    abstract fun bindStatsRepository(impl: OfflineFirstStatsRepository): StatsRepository
}
