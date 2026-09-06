package cz.dcervenka.choretracker.core.data.repository

import cz.dcervenka.choretracker.core.data.contract.StatsRepository
import cz.dcervenka.choretracker.core.data.mapper.asModel
import cz.dcervenka.choretracker.core.data.mapper.asModels
import cz.dcervenka.choretracker.core.database.dao.ChoreDao
import cz.dcervenka.choretracker.core.database.dao.CompletionDao
import cz.dcervenka.choretracker.core.database.dao.CompletionParticipantDao
import cz.dcervenka.choretracker.core.database.dao.HouseholdDao
import cz.dcervenka.choretracker.core.database.dao.MemberDao
import cz.dcervenka.choretracker.core.database.entity.ChoreEntity
import cz.dcervenka.choretracker.core.database.entity.HouseholdEntity
import cz.dcervenka.choretracker.core.database.entity.MemberEntity
import cz.dcervenka.choretracker.core.model.household.Household
import cz.dcervenka.choretracker.core.model.stats.HouseholdStatsInput
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

@Singleton
class OfflineFirstStatsRepository @Inject constructor(
    private val householdDao: HouseholdDao,
    private val memberDao: MemberDao,
    private val choreDao: ChoreDao,
    private val completionDao: CompletionDao,
    private val participantDao: CompletionParticipantDao,
) : StatsRepository {

    override fun observeHouseholdStatsInput(householdId: String): Flow<HouseholdStatsInput> =
        combine(
            householdDao.observeHousehold(householdId),
            memberDao.observeMembers(householdId),
            choreDao.observeChores(householdId),
            completionDao.observeCompletions(householdId),
            participantDao.observeParticipants(householdId),
        ) { household, members, chores, completions, participants ->
            HouseholdStatsInput(
                household = household.asModelOrPlaceholder(householdId),
                members = members.map(MemberEntity::asModel),
                chores = chores.map(ChoreEntity::asModel),
                completions = completions.asModels(participants),
            )
        }

    override suspend fun getHouseholdStatsInput(householdId: String): HouseholdStatsInput = coroutineScope {
        val household = async { householdDao.getHousehold(householdId) }
        val members = async { memberDao.getMembers(householdId) }
        val chores = async { choreDao.getChores(householdId) }
        val completions = async { completionDao.getCompletions(householdId) }
        val participants = async { participantDao.getParticipants(householdId) }
        HouseholdStatsInput(
            household = household.await().asModelOrPlaceholder(householdId),
            members = members.await().map(MemberEntity::asModel),
            chores = chores.await().map(ChoreEntity::asModel),
            completions = completions.await().asModels(participants.await()),
        )
    }

    // Stats can be requested (e.g. by the reminder worker) before the household row has finished
    // its first sync - a placeholder keeps the calculation running on whatever local data already
    // exists instead of surfacing an error for a purely transient local-data gap.
    private fun HouseholdEntity?.asModelOrPlaceholder(householdId: String): Household =
        this?.asModel() ?: Household(
            id = householdId,
            name = "Household",
            ownerUserId = "",
            inviteCode = "",
            createdAt = Clock.System.now(),
        )
}
