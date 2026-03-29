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
import cz.dcervenka.choretracker.core.database.entity.MemberEntity
import cz.dcervenka.choretracker.core.domain.HouseholdStatisticsCalculator
import cz.dcervenka.choretracker.core.model.household.Household
import cz.dcervenka.choretracker.core.model.stats.DashboardSnapshot
import cz.dcervenka.choretracker.core.model.stats.StatsSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

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
