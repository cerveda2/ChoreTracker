package cz.dcervenka.choretracker.core.domain

import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.chore.ChoreCompletion
import cz.dcervenka.choretracker.core.model.household.Household
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.stats.ChoreComparison
import cz.dcervenka.choretracker.core.model.stats.ChoreStaleness
import cz.dcervenka.choretracker.core.model.stats.DashboardSnapshot
import cz.dcervenka.choretracker.core.model.stats.MemberContribution
import cz.dcervenka.choretracker.core.model.stats.MonthlyBreakdown
import cz.dcervenka.choretracker.core.model.stats.RecentCompletion
import cz.dcervenka.choretracker.core.model.stats.StatsSnapshot
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

private const val NEEDS_ATTENTION_THRESHOLD_DAYS = 14
private const val SOON_THRESHOLD_DAYS = 7
private const val MONTHLY_BREAKDOWN_LIMIT = 6

class HouseholdStatisticsCalculator @Inject constructor() {

    fun dashboardSnapshot(
        household: Household,
        members: List<HouseholdMember>,
        chores: List<Chore>,
        completions: List<ChoreCompletion>,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        today: LocalDate,
        recentLimit: Int = 8,
    ): DashboardSnapshot = DashboardSnapshot(
        household = household,
        memberContributions = buildContributions(
            members = members,
            completions = completions,
            timeZone = timeZone,
            today = today,
        ),
        activeChores = chores.filter { it.isActive && it.deletedAt == null }.sortedBy(Chore::name),
        recentCompletions = buildRecent(
            chores = chores,
            members = members,
            completions = completions,
            recentLimit = recentLimit,
        ),
        staleChores = buildStaleness(
            chores = chores,
            completions = completions,
            timeZone = timeZone,
            today = today,
        ),
    )

    fun statsSnapshot(
        household: Household,
        members: List<HouseholdMember>,
        chores: List<Chore>,
        completions: List<ChoreCompletion>,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        today: LocalDate,
    ): StatsSnapshot = StatsSnapshot(
        household = household,
        comparisons = buildComparisons(
            chores = chores,
            members = members,
            completions = completions,
        ),
        monthlyBreakdown = buildMonthlyBreakdown(
            members = members,
            completions = completions,
            timeZone = timeZone,
        ),
        staleChores = buildStaleness(
            chores = chores,
            completions = completions,
            timeZone = timeZone,
            today = today,
        ),
    )

    private fun buildRecent(
        chores: List<Chore>,
        members: List<HouseholdMember>,
        completions: List<ChoreCompletion>,
        recentLimit: Int,
    ): List<RecentCompletion> {
        val choreMap = chores.associateBy(Chore::id)
        val memberMap = members.associateBy(HouseholdMember::id)
        return completions.sortedByDescending(ChoreCompletion::createdAt)
            .take(recentLimit)
            .map { completion ->
                RecentCompletion(
                    completionId = completion.id,
                    choreName = choreMap[completion.choreId]?.name.orEmpty(),
                    note = completion.note,
                    completedAt = completion.createdAt,
                    participantNames = completion.participantMemberIds
                        .mapNotNull { memberId -> memberMap[memberId]?.displayName },
                )
            }
    }

    private fun buildContributions(
        members: List<HouseholdMember>,
        completions: List<ChoreCompletion>,
        timeZone: TimeZone,
        today: LocalDate,
    ): List<MemberContribution> {
        val thirtyDaysAgo = today.minus(DatePeriod(days = 30))
        return members.map { member ->
            val memberCompletions = completions.filter { completion ->
                member.id in completion.participantMemberIds
            }
            MemberContribution(
                memberId = member.id,
                displayName = member.displayName,
                totalCount = memberCompletions.size,
                last30DaysCount = memberCompletions.count { completion ->
                    completion.createdAt.toLocalDateTime(timeZone).date >= thirtyDaysAgo
                },
                currentMonthCount = memberCompletions.count { completion ->
                    val date = completion.createdAt.toLocalDateTime(timeZone).date
                    date.year == today.year && date.month == today.month
                },
            )
        }
    }

    private fun buildComparisons(
        chores: List<Chore>,
        members: List<HouseholdMember>,
        completions: List<ChoreCompletion>,
    ): List<ChoreComparison> = chores
        .filter { it.deletedAt == null }
        .sortedBy(Chore::name)
        .map { chore ->
            val choreCompletions = completions.filter { it.choreId == chore.id }
            val countsByMember = members.associate { member ->
                member.displayName to choreCompletions.count { completion ->
                    member.id in completion.participantMemberIds
                }
            }
            val topCount = countsByMember.values.maxOrNull() ?: 0
            val leaderLabel = when {
                choreCompletions.isEmpty() || topCount == 0 -> "No data"
                countsByMember.values.count { it == topCount } > 1 -> "Tie"
                else -> countsByMember.maxByOrNull { it.value }?.key ?: "No data"
            }
            ChoreComparison(
                choreId = chore.id,
                choreName = chore.name,
                countsByMember = countsByMember,
                leaderLabel = leaderLabel,
                totalCount = choreCompletions.size,
            )
        }

    private fun buildMonthlyBreakdown(
        members: List<HouseholdMember>,
        completions: List<ChoreCompletion>,
        timeZone: TimeZone,
    ): List<MonthlyBreakdown> = completions
        .groupBy { completion ->
            val date = completion.createdAt.toLocalDateTime(timeZone).date
            "${date.year}-${(date.month.ordinal + 1).toString().padStart(2, '0')}"
        }
        .entries
        .sortedByDescending { it.key }
        .take(MONTHLY_BREAKDOWN_LIMIT)
        .map { (monthLabel, monthCompletions) ->
            MonthlyBreakdown(
                monthLabel = monthLabel,
                countsByMember = members.associate { member ->
                    member.displayName to monthCompletions.count { completion ->
                        member.id in completion.participantMemberIds
                    }
                },
                totalCount = monthCompletions.size,
            )
        }

    private fun buildStaleness(
        chores: List<Chore>,
        completions: List<ChoreCompletion>,
        timeZone: TimeZone,
        today: LocalDate,
    ): List<ChoreStaleness> = chores
        .filter { it.deletedAt == null }
        .sortedBy(Chore::name)
        .map { chore ->
            val lastCompletionDate = completions
                .filter { it.choreId == chore.id }
                .maxByOrNull(ChoreCompletion::createdAt)
                ?.createdAt
                ?.toLocalDateTime(timeZone)
                ?.date
            val daysSinceLastCompletion = lastCompletionDate?.daysUntil(today)
            ChoreStaleness(
                choreId = chore.id,
                choreName = chore.name,
                lastCompletedDate = lastCompletionDate,
                daysSinceLastCompletion = daysSinceLastCompletion,
                status = when {
                    daysSinceLastCompletion == null -> "Never"
                    daysSinceLastCompletion >= NEEDS_ATTENTION_THRESHOLD_DAYS -> "Needs attention"
                    daysSinceLastCompletion >= SOON_THRESHOLD_DAYS -> "Soon"
                    else -> "OK"
                },
            )
        }
}
