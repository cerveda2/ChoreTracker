package cz.dcervenka.choretracker.core.domain

import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.chore.ChoreCompletion
import cz.dcervenka.choretracker.core.model.household.Household
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.stats.CategoryComparison
import cz.dcervenka.choretracker.core.model.stats.ChoreComparison
import cz.dcervenka.choretracker.core.model.stats.ChoreLeaderResult
import cz.dcervenka.choretracker.core.model.stats.ChoreStaleness
import cz.dcervenka.choretracker.core.model.stats.ChoreStatus
import cz.dcervenka.choretracker.core.model.stats.DashboardSnapshot
import cz.dcervenka.choretracker.core.model.stats.HouseholdSummary
import cz.dcervenka.choretracker.core.model.stats.MemberContribution
import cz.dcervenka.choretracker.core.model.stats.MonthlyBreakdown
import cz.dcervenka.choretracker.core.model.stats.RecentCompletion
import cz.dcervenka.choretracker.core.model.stats.StatsSnapshot
import cz.dcervenka.choretracker.core.model.stats.TopContributorResult
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import kotlin.math.roundToInt

private const val DEFAULT_NEEDS_ATTENTION_THRESHOLD_DAYS = 14
private const val DEFAULT_SOON_THRESHOLD_DAYS = 7
private const val SOON_THRESHOLD_RATIO = 0.8
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
    ): DashboardSnapshot {
        val activeCompletions = activeChoreCompletions(chores, completions)
        val contributions = buildContributions(
            members = members,
            completions = activeCompletions,
            timeZone = timeZone,
            today = today,
        )
        return DashboardSnapshot(
            household = household,
            summary = buildSummary(contributions, activeCompletions),
            memberContributions = contributions,
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
    }

    fun statsSnapshot(
        household: Household,
        members: List<HouseholdMember>,
        chores: List<Chore>,
        completions: List<ChoreCompletion>,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        today: LocalDate,
    ): StatsSnapshot {
        val activeCompletions = activeChoreCompletions(chores, completions)
        val contributions = buildContributions(
            members = members,
            completions = activeCompletions,
            timeZone = timeZone,
            today = today,
        )
        return StatsSnapshot(
            household = household,
            summary = buildSummary(contributions, activeCompletions),
            memberContributions = contributions,
            comparisons = buildComparisons(
                chores = chores,
                members = members,
                completions = activeCompletions,
            ),
            categoryComparisons = buildCategoryComparisons(
                chores = chores,
                members = members,
                completions = activeCompletions,
            ),
            monthlyBreakdown = buildMonthlyBreakdown(
                members = members,
                completions = activeCompletions,
                timeZone = timeZone,
                today = today,
            ),
            staleChores = buildStaleness(
                chores = chores,
                completions = completions,
                timeZone = timeZone,
                today = today,
            ),
        )
    }

    // buildComparisons/buildCategoryComparisons already exclude a deleted chore's completions
    // (they only iterate non-deleted chores); buildContributions/buildSummary/buildMonthlyBreakdown
    // didn't, so a deleted chore's completions were counted in the household total and each
    // member's personal total but silently absent from every per-chore/category/month breakdown -
    // the numbers could never be cross-checked against each other. buildRecent and buildStaleness
    // intentionally keep the raw, unfiltered completions - chore history and past-chore names
    // should still show up after the chore itself is deleted.
    private fun activeChoreCompletions(chores: List<Chore>, completions: List<ChoreCompletion>): List<ChoreCompletion> {
        val activeChoreIds = chores.filter { it.deletedAt == null }.map { it.id }.toSet()
        return completions.filter { it.choreId in activeChoreIds }
    }

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
                    participantMemberIds = completion.participantMemberIds,
                )
            }
    }

    private fun buildContributions(
        members: List<HouseholdMember>,
        completions: List<ChoreCompletion>,
        timeZone: TimeZone,
        today: LocalDate,
    ): List<MemberContribution> {
        // 29, not 30: today counts as day 0, so today - 29 is a 30-day window inclusive of today.
        val thirtyDaysAgo = today.minus(DatePeriod(days = 29))
        // A removed member's completion_participants rows aren't cleaned up (see
        // OfflineFirstHouseholdRepository.deleteMember), so participantMemberIds can still
        // reference a member no longer in this list. Counting those in the denominator would
        // make the remaining members' shares never add up to 100%.
        val currentMemberIds = members.map { it.id }.toSet()
        val totalAcrossAll = completions.sumOf { completion ->
            completion.participantMemberIds.count { it in currentMemberIds }
        }
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
                sharePercent = if (totalAcrossAll > 0) {
                    (memberCompletions.size * 100) / totalAcrossAll
                } else {
                    0
                },
            )
        }
    }

    // totalCompletions counts completions (a shared one counts once), matching
    // ChoreComparison/CategoryComparison/MonthlyBreakdown.totalCount - not
    // contributions.sumOf { it.totalCount }, which counts participant slots (a shared completion
    // counts once per participant) and could never be reconciled against those other totals.
    private fun buildSummary(
        contributions: List<MemberContribution>,
        completions: List<ChoreCompletion>,
    ): HouseholdSummary {
        val topCount = contributions.maxOfOrNull { it.totalCount } ?: 0
        val topContributor = when {
            topCount == 0 -> TopContributorResult.NoData
            contributions.count { it.totalCount == topCount } > 1 -> TopContributorResult.Tie
            else -> contributions.first { it.totalCount == topCount }
                .let { TopContributorResult.Leader(it.displayName, it.sharePercent) }
        }
        return HouseholdSummary(totalCompletions = completions.size, topContributor = topContributor)
    }

    // Keyed by member id, not display name: two members sharing a display name would otherwise
    // silently collapse into one entry (Map can't have two different values under one key).
    private fun buildCountsByMemberId(
        members: List<HouseholdMember>,
        relevantCompletions: List<ChoreCompletion>,
    ): Map<String, Int> = members.associate { member ->
        member.id to relevantCompletions.count { completion -> member.id in completion.participantMemberIds }
    }

    private fun computeLeader(
        countsByMemberId: Map<String, Int>,
        hasRelevantCompletions: Boolean,
        members: List<HouseholdMember>,
    ): ChoreLeaderResult {
        val topCount = countsByMemberId.values.maxOrNull() ?: 0
        return when {
            !hasRelevantCompletions || topCount == 0 -> ChoreLeaderResult.NoData
            countsByMemberId.values.count { it == topCount } > 1 -> ChoreLeaderResult.Tie
            else -> countsByMemberId.maxByOrNull { it.value }
                ?.key
                ?.let { leaderId -> members.find { it.id == leaderId }?.displayName }
                ?.let(ChoreLeaderResult::Leader)
                ?: ChoreLeaderResult.NoData
        }
    }

    private fun buildCategoryComparisons(
        chores: List<Chore>,
        members: List<HouseholdMember>,
        completions: List<ChoreCompletion>,
    ): List<CategoryComparison> = chores
        .filter { it.deletedAt == null }
        .groupBy { it.category }
        .entries
        .sortedBy { it.key.name }
        .map { (category, categoryChores) ->
            val choreIds = categoryChores.map { it.id }.toSet()
            val categoryCompletions = completions.filter { it.choreId in choreIds }
            val counts = buildCountsByMemberId(members, categoryCompletions)
            CategoryComparison(
                category = category,
                choreCount = categoryChores.size,
                countsByMemberId = counts,
                totalCount = categoryCompletions.size,
                leader = computeLeader(counts, categoryCompletions.isNotEmpty(), members),
            )
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
            val counts = buildCountsByMemberId(members, choreCompletions)
            ChoreComparison(
                choreId = chore.id,
                choreName = chore.name,
                countsByMemberId = counts,
                leader = computeLeader(counts, choreCompletions.isNotEmpty(), members),
                totalCount = choreCompletions.size,
            )
        }

    // The trailing MONTHLY_BREAKDOWN_LIMIT calendar months ending at `today`, zero-filled - not
    // "the most recent N months that happen to have a completion in them", which could skip
    // months entirely or (for a household with sparse history) reach back years, and made the
    // chart's x-axis non-contiguous.
    private fun buildMonthlyBreakdown(
        members: List<HouseholdMember>,
        completions: List<ChoreCompletion>,
        timeZone: TimeZone,
        today: LocalDate,
    ): List<MonthlyBreakdown> {
        val completionsByMonth = completions.groupBy { completion ->
            monthLabel(completion.createdAt.toLocalDateTime(timeZone).date)
        }
        return (0 until MONTHLY_BREAKDOWN_LIMIT).map { monthsAgo ->
            val label = monthLabel(today.minus(DatePeriod(months = monthsAgo)))
            val monthCompletions = completionsByMonth[label].orEmpty()
            MonthlyBreakdown(
                monthLabel = label,
                countsByMemberId = buildCountsByMemberId(members, monthCompletions),
                totalCount = monthCompletions.size,
            )
        }
    }

    private fun monthLabel(date: LocalDate): String =
        "${date.year}-${(date.month.ordinal + 1).toString().padStart(2, '0')}"

    fun buildStaleness(
        chores: List<Chore>,
        completions: List<ChoreCompletion>,
        timeZone: TimeZone,
        today: LocalDate,
    ): List<ChoreStaleness> = chores
        .filter { it.isActive && it.deletedAt == null }
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
                frequencyDays = chore.frequencyDays,
                status = computeStatus(
                    daysSinceLastCompletion = daysSinceLastCompletion,
                    frequencyDays = chore.frequencyDays,
                ),
            )
        }

    private fun computeStatus(daysSinceLastCompletion: Int?, frequencyDays: Int?): ChoreStatus {
        if (daysSinceLastCompletion == null) return ChoreStatus.NEVER
        val attentionThreshold = if (frequencyDays != null && frequencyDays > 0) {
            frequencyDays
        } else {
            DEFAULT_NEEDS_ATTENTION_THRESHOLD_DAYS
        }
        val soonThreshold = if (frequencyDays == 1) {
            // A 1-day cycle has no integer day count strictly between "just done" (0) and "due"
            // (1) to hold a distinct SOON phase - clamping to attentionThreshold - 1 (below) would
            // give 0, which makes daysSinceLastCompletion >= soonThreshold true from the moment a
            // chore is logged (day 0), so it could never read OK. Equal to attentionThreshold
            // instead: SOON stays unreachable, but so does the false-overdue-on-day-0 read.
            attentionThreshold
        } else if (frequencyDays != null && frequencyDays > 0) {
            // For a 2-day frequency, rounding frequencyDays * SOON_THRESHOLD_RATIO lands on
            // attentionThreshold itself (frequencyDays=2 → round(1.6)=2), which made the
            // NEEDS_ATTENTION branch below always win first and SOON unreachable. Clamping below
            // attentionThreshold keeps SOON reachable for short frequencies too.
            (frequencyDays * SOON_THRESHOLD_RATIO).roundToInt().coerceAtMost(attentionThreshold - 1)
        } else {
            DEFAULT_SOON_THRESHOLD_DAYS
        }
        return when {
            daysSinceLastCompletion >= attentionThreshold -> ChoreStatus.NEEDS_ATTENTION
            daysSinceLastCompletion >= soonThreshold -> ChoreStatus.SOON
            else -> ChoreStatus.OK
        }
    }
}
