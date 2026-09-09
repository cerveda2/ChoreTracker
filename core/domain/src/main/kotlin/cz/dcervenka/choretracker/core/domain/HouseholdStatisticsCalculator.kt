package cz.dcervenka.choretracker.core.domain

import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.chore.ChoreCompletion
import cz.dcervenka.choretracker.core.model.household.Household
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.stats.BalanceSummary
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
import cz.dcervenka.choretracker.core.model.stats.ShareBreakdown
import cz.dcervenka.choretracker.core.model.stats.StatsPeriod
import cz.dcervenka.choretracker.core.model.stats.StatsSnapshot
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
private const val PERCENT_TOTAL = 100

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
            summary = buildSummary(activeCompletions),
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
                members = members,
                timeZone = timeZone,
                today = today,
            ),
            balance = buildBalance(contributions),
        )
    }

    fun statsSnapshot(
        household: Household,
        members: List<HouseholdMember>,
        chores: List<Chore>,
        completions: List<ChoreCompletion>,
        period: StatsPeriod,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        today: LocalDate,
    ): StatsSnapshot {
        val activeCompletions = activeChoreCompletions(chores, completions)
        // The monthly trend chart and chore staleness are deliberately NOT period-filtered - the
        // chart always covers its own fixed trailing window, and "how stale is this chore" needs
        // its true last completion regardless of which reporting period is selected.
        val periodCompletions = filterByPeriod(activeCompletions, period, timeZone, today)
        return StatsSnapshot(
            household = household,
            summary = buildSummary(periodCompletions),
            memberContributions = buildContributions(
                members = members,
                completions = periodCompletions,
                timeZone = timeZone,
                today = today,
            ),
            shareBreakdown = buildShareBreakdown(members, periodCompletions),
            comparisons = buildComparisons(
                chores = chores,
                members = members,
                completions = periodCompletions,
                allCompletions = activeCompletions,
            ),
            categoryComparisons = buildCategoryComparisons(
                chores = chores,
                members = members,
                completions = periodCompletions,
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
                members = members,
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
            )
        }
    }

    // totalCompletions counts completions (a shared one counts once), matching
    // ChoreComparison/CategoryComparison/MonthlyBreakdown.totalCount - not
    // contributions.sumOf { it.totalCount }, which counts participant slots (a shared completion
    // counts once per participant) and could never be reconciled against those other totals.
    private fun buildSummary(completions: List<ChoreCompletion>): HouseholdSummary =
        HouseholdSummary(totalCompletions = completions.size)

    // A removed member's completion_participants rows aren't cleaned up (see
    // OfflineFirstHouseholdRepository.deleteMember), so participantMemberIds can still reference
    // a member no longer in this list - those completions are excluded entirely (from both solo
    // and shared counts) rather than left in a denominator the remaining members' percentages
    // could never add up against.
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
        allCompletions: List<ChoreCompletion>,
    ): List<ChoreComparison> = chores
        .filter { it.deletedAt == null }
        .sortedBy(Chore::name)
        .map { chore ->
            val choreCompletions = completions.filter { it.choreId == chore.id }
            val counts = buildCountsByMemberId(members, choreCompletions)
            // Full history (allCompletions), not the period-filtered `completions` above - a
            // low-cadence chore can easily have zero completions in the selected reporting
            // period, which would otherwise make "whose turn" disappear (or flip) purely because
            // of which period is selected, rather than reflecting who actually went last. Mirrors
            // why staleChores/monthlyBreakdown above are also computed off unfiltered completions.
            val lastCompleterIds = allCompletions.filter { it.choreId == chore.id }
                .maxByOrNull(ChoreCompletion::createdAt)
                ?.participantMemberIds
                .orEmpty()
                .toSet()
            ChoreComparison(
                choreId = chore.id,
                choreName = chore.name,
                countsByMemberId = counts,
                leader = computeLeader(counts, choreCompletions.isNotEmpty(), members),
                totalCount = choreCompletions.size,
                nextTurnMemberId = computeNextTurn(counts, lastCompleterIds),
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
        members: List<HouseholdMember>,
        timeZone: TimeZone,
        today: LocalDate,
    ): List<ChoreStaleness> {
        val memberMap = members.associateBy(HouseholdMember::id)
        return chores
            .filter { it.isActive && it.deletedAt == null }
            .sortedBy(Chore::name)
            .map { chore ->
                val lastCompletion = completions
                    .filter { it.choreId == chore.id }
                    .maxByOrNull(ChoreCompletion::createdAt)
                val lastCompletionDate = lastCompletion?.createdAt?.toLocalDateTime(timeZone)?.date
                val daysSinceLastCompletion = lastCompletionDate?.daysUntil(today)
                val frequencyDays = chore.frequencyDays
                ChoreStaleness(
                    choreId = chore.id,
                    choreName = chore.name,
                    lastCompletedDate = lastCompletionDate,
                    daysSinceLastCompletion = daysSinceLastCompletion,
                    frequencyDays = frequencyDays,
                    status = computeStatus(
                        daysSinceLastCompletion = daysSinceLastCompletion,
                        frequencyDays = frequencyDays,
                    ),
                    lastCompletedByNames = lastCompletion?.participantMemberIds
                        ?.mapNotNull { memberId -> memberMap[memberId]?.displayName }
                        .orEmpty(),
                    dueInDays = if (frequencyDays != null && daysSinceLastCompletion != null) {
                        frequencyDays - daysSinceLastCompletion
                    } else {
                        null
                    },
                )
            }
    }

    private fun computeStatus(daysSinceLastCompletion: Int?, frequencyDays: Int?): ChoreStatus {
        if (daysSinceLastCompletion == null) return ChoreStatus.NEVER
        val attentionThreshold = if (frequencyDays != null && frequencyDays > 0) {
            frequencyDays
        } else {
            DEFAULT_NEEDS_ATTENTION_THRESHOLD_DAYS
        }
        val soonThreshold = if (frequencyDays != null && frequencyDays > 0) {
            // For a 1- or 2-day frequency, rounding frequencyDays * SOON_THRESHOLD_RATIO lands on
            // attentionThreshold itself (e.g. frequencyDays=2 → round(1.6)=2), which made the
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

// Top-level, not a method: it only reads its parameter (no instance state), and keeping it out
// of the class avoids tripping TooManyFunctions there. Null with fewer than two members, or when
// nobody has logged anything in the last 30 days - "who's carrying more" is meaningless either way.
private fun buildBalance(contributions: List<MemberContribution>): BalanceSummary? {
    if (contributions.size < 2) return null
    val countsByMemberId = contributions.associate { it.memberId to it.last30DaysCount }
    return if (countsByMemberId.values.all { it == 0 }) {
        null
    } else {
        val leader = contributions.maxBy { it.last30DaysCount }
        val lagging = contributions.minBy { it.last30DaysCount }
        BalanceSummary(
            leaderMemberId = leader.memberId,
            laggingMemberId = lagging.memberId,
            gap = leader.last30DaysCount - lagging.last30DaysCount,
            countsByMemberId = countsByMemberId,
        )
    }
}

// Standard largest-remainder-method rounding: floor every share, then hand the leftover
// percentage points (100 - sum of floors) one at a time to whichever entries had the largest
// fractional remainder, so the result always sums to exactly 100 (never 99 or 101).
private fun largestRemainderPercentages(counts: List<Int>, total: Int): List<Int> {
    if (total <= 0) return counts.map { 0 }
    val exact = counts.map { it * PERCENT_TOTAL.toDouble() / total }
    val floors = exact.map { it.toInt() }
    val remainder = PERCENT_TOTAL - floors.sum()
    val remainderOrder = exact.indices.sortedByDescending { exact[it] - floors[it] }
    val result = floors.toMutableList()
    remainderOrder.take(remainder).forEach { index -> result[index] += 1 }
    return result
}

// A removed member's completion_participants rows aren't cleaned up (see
// OfflineFirstHouseholdRepository.deleteMember), so participantMemberIds can still reference a
// member no longer in this list - those completions are excluded entirely (from both solo and
// shared counts) rather than left in a denominator the remaining members' percentages could never
// add up against.
private fun buildShareBreakdown(members: List<HouseholdMember>, completions: List<ChoreCompletion>): ShareBreakdown {
    val currentMemberIds = members.map { it.id }.toSet()
    val soloCountByMemberId = members.associate { it.id to 0 }.toMutableMap()
    var sharedCount = 0
    completions.forEach { completion ->
        val currentParticipants = completion.participantMemberIds.filter { it in currentMemberIds }.distinct()
        when {
            currentParticipants.size == 1 ->
                soloCountByMemberId[currentParticipants[0]] = (soloCountByMemberId[currentParticipants[0]] ?: 0) + 1
            currentParticipants.size > 1 -> sharedCount += 1
            // else: no current-member participant left - excluded from every count.
        }
    }
    val totalCount = soloCountByMemberId.values.sum() + sharedCount
    val memberIds = members.map { it.id }
    val counts = memberIds.map { soloCountByMemberId[it] ?: 0 } + sharedCount
    val percentages = largestRemainderPercentages(counts, totalCount)
    val percentByMemberId = memberIds.indices.associate { index -> memberIds[index] to percentages[index] }
    return ShareBreakdown(
        soloCountByMemberId = soloCountByMemberId,
        sharedCount = sharedCount,
        totalCount = totalCount,
        percentByMemberId = percentByMemberId,
        togetherPercent = percentages.lastOrNull() ?: 0,
    )
}

private fun periodStart(period: StatsPeriod, today: LocalDate): LocalDate? = when (period) {
    // 6 (or 29) days ago, not 7 (or 30): today counts as day 0, so the window is inclusive
    // of today - matches the existing last30DaysCount convention used for last30DaysCount.
    StatsPeriod.WEEK -> today.minus(DatePeriod(days = 6))
    StatsPeriod.MONTH -> today.minus(DatePeriod(days = 29))
    StatsPeriod.SIX_MONTHS -> today.minus(DatePeriod(months = 6))
    StatsPeriod.ALL -> null
}

private fun filterByPeriod(
    completions: List<ChoreCompletion>,
    period: StatsPeriod,
    timeZone: TimeZone,
    today: LocalDate,
): List<ChoreCompletion> {
    val start = periodStart(period, today) ?: return completions
    return completions.filter { it.createdAt.toLocalDateTime(timeZone).date >= start }
}

// Lowest count takes the next turn; a tie is broken toward whoever wasn't part of the most
// recent completion (shared completions can leave more than one "last completer"). Still tied
// after that, or nobody to choose from at all - null, rather than guessing.
private fun computeNextTurn(countsByMemberId: Map<String, Int>, lastCompleterIds: Set<String>): String? {
    if (countsByMemberId.isEmpty()) return null
    val minCount = countsByMemberId.values.min()
    val candidates = countsByMemberId.filterValues { it == minCount }.keys
    return if (candidates.size == 1) {
        candidates.first()
    } else {
        candidates.filterNot { it in lastCompleterIds }.singleOrNull()
    }
}
