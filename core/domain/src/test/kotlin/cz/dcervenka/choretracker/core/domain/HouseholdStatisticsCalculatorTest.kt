package cz.dcervenka.choretracker.core.domain

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.chore.ChoreCompletion
import cz.dcervenka.choretracker.core.model.household.Household
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import cz.dcervenka.choretracker.core.model.stats.ChoreLeaderResult
import cz.dcervenka.choretracker.core.model.stats.ChoreStatus
import cz.dcervenka.choretracker.core.model.stats.TopContributorResult
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Test
import kotlin.time.Instant

class HouseholdStatisticsCalculatorTest {

    private val calculator = HouseholdStatisticsCalculator()
    private val timeZone = TimeZone.UTC
    private val today = LocalDate(2026, 3, 29)

    private val household = Household(
        id = "household-1",
        name = "Home",
        ownerUserId = "user-alice",
        inviteCode = "ABCD1234",
        createdAt = Instant.parse("2026-01-01T09:00:00Z"),
    )

    private val members = listOf(
        HouseholdMember(
            id = "member-alice",
            householdId = household.id,
            userId = "user-alice",
            displayName = "Alice",
            role = HouseholdRole.OWNER,
            isCurrentUser = true,
        ),
        HouseholdMember(
            id = "member-bob",
            householdId = household.id,
            userId = "user-bob",
            displayName = "Bob",
            role = HouseholdRole.MEMBER,
        ),
    )

    private val chores = listOf(
        Chore(
            id = "chore-dishes",
            householdId = household.id,
            name = "Dishes",
            isActive = true,
            createdAt = Instant.parse("2026-01-02T09:00:00Z"),
        ),
        Chore(
            id = "chore-vacuum",
            householdId = household.id,
            name = "Vacuum",
            isActive = true,
            createdAt = Instant.parse("2026-01-03T09:00:00Z"),
        ),
        Chore(
            id = "chore-dusting",
            householdId = household.id,
            name = "Dusting",
            isActive = true,
            createdAt = Instant.parse("2026-01-04T09:00:00Z"),
        ),
    )

    @Test
    fun `dashboard counts shared completions for every participant and computes staleness`() {
        val completions = listOf(
            completion(
                id = "completion-1",
                choreId = "chore-dishes",
                createdAt = "2026-03-28T18:00:00Z",
                participantMemberIds = listOf("member-alice", "member-bob"),
            ),
            completion(
                id = "completion-2",
                choreId = "chore-dishes",
                createdAt = "2026-03-10T18:00:00Z",
                participantMemberIds = listOf("member-alice"),
            ),
            completion(
                id = "completion-3",
                choreId = "chore-vacuum",
                createdAt = "2026-03-05T18:00:00Z",
                participantMemberIds = listOf("member-bob"),
            ),
        )

        val dashboard = calculator.dashboardSnapshot(
            household = household,
            members = members,
            chores = chores,
            completions = completions,
            timeZone = timeZone,
            today = today,
        )

        val contributions = dashboard.memberContributions.associateBy { it.displayName }
        assertThat(contributions["Alice"]?.totalCount).isEqualTo(2)
        assertThat(contributions["Alice"]?.last30DaysCount).isEqualTo(2)
        assertThat(contributions["Alice"]?.currentMonthCount).isEqualTo(2)
        assertThat(contributions["Alice"]?.sharePercent).isEqualTo(50)
        assertThat(contributions["Bob"]?.totalCount).isEqualTo(2)
        assertThat(contributions["Bob"]?.last30DaysCount).isEqualTo(2)
        assertThat(contributions["Bob"]?.currentMonthCount).isEqualTo(2)
        assertThat(contributions["Bob"]?.sharePercent).isEqualTo(50)
        // 3 completions total (completion-1 is shared, so it's one completion, not two) -
        // matches the definition ChoreComparison/CategoryComparison/MonthlyBreakdown.totalCount
        // already use, not the sum of each member's own totalCount (which double-counts a shared
        // completion once per participant).
        assertThat(dashboard.summary.totalCompletions).isEqualTo(3)
        // Alice and Bob both have totalCount 2 - a genuine tie, not an arbitrary pick.
        assertThat(dashboard.summary.topContributor).isEqualTo(TopContributorResult.Tie)

        assertThat(dashboard.recentCompletions.first().participantNames).containsExactly("Alice", "Bob").inOrder()

        val staleness = dashboard.staleChores.associateBy { it.choreName }
        assertThat(staleness["Dishes"]?.status).isEqualTo(ChoreStatus.OK)
        assertThat(staleness["Vacuum"]?.status).isEqualTo(ChoreStatus.NEEDS_ATTENTION)
        assertThat(staleness["Dusting"]?.status).isEqualTo(ChoreStatus.NEVER)
    }

    @Test
    fun `last30DaysCount is exactly a 30-day window, inclusive of both endpoints`() {
        val completions = listOf(
            // Exactly 29 days before today - the oldest day that should still count.
            completion(
                id = "completion-in-window",
                choreId = "chore-dishes",
                createdAt = "2026-02-28T18:00:00Z",
                participantMemberIds = listOf("member-alice"),
            ),
            // One day older than that - the boundary case the original off-by-one bug miscounted.
            completion(
                id = "completion-out-of-window",
                choreId = "chore-dishes",
                createdAt = "2026-02-27T18:00:00Z",
                participantMemberIds = listOf("member-alice"),
            ),
        )

        val dashboard = calculator.dashboardSnapshot(
            household = household,
            members = members,
            chores = chores,
            completions = completions,
            timeZone = timeZone,
            today = today,
        )

        val alice = dashboard.memberContributions.first { it.displayName == "Alice" }
        assertThat(alice.totalCount).isEqualTo(2)
        assertThat(alice.last30DaysCount).isEqualTo(1)
    }

    @Test
    fun `a deleted chore's completions are excluded from the summary and monthly breakdown too`() {
        val activeChore = Chore(
            id = "chore-active",
            householdId = household.id,
            name = "Dishes",
            isActive = true,
            createdAt = Instant.parse("2026-01-02T09:00:00Z"),
        )
        val deletedChore = Chore(
            id = "chore-deleted",
            householdId = household.id,
            name = "Old chore",
            isActive = false,
            createdAt = Instant.parse("2026-01-02T09:00:00Z"),
            deletedAt = Instant.parse("2026-02-01T09:00:00Z"),
        )
        val completions = listOf(
            completion(
                id = "completion-active",
                choreId = "chore-active",
                createdAt = "2026-03-28T18:00:00Z",
                participantMemberIds = listOf("member-alice"),
            ),
            completion(
                id = "completion-deleted",
                choreId = "chore-deleted",
                createdAt = "2026-03-27T18:00:00Z",
                participantMemberIds = listOf("member-alice"),
            ),
        )

        val stats = calculator.statsSnapshot(
            household = household,
            members = members,
            chores = listOf(activeChore, deletedChore),
            completions = completions,
            timeZone = timeZone,
            today = today,
        )

        // Matches comparisons/categoryComparisons, which already only iterate non-deleted
        // chores - before this fix, the summary/monthly totals below would have been 2, not 1.
        assertThat(stats.summary.totalCompletions).isEqualTo(1)
        assertThat(stats.memberContributions.first { it.displayName == "Alice" }.totalCount).isEqualTo(1)
        assertThat(stats.monthlyBreakdown.first().totalCount).isEqualTo(1)
    }

    @Test
    fun `topContributor is NoData when nobody has completed anything yet`() {
        val dashboard = calculator.dashboardSnapshot(
            household = household,
            members = members,
            chores = chores,
            completions = emptyList(),
            timeZone = timeZone,
            today = today,
        )

        assertThat(dashboard.summary.totalCompletions).isEqualTo(0)
        assertThat(dashboard.summary.topContributor).isEqualTo(TopContributorResult.NoData)
    }

    @Test
    fun `sharePercent sums to 100 across current members even after a member is removed`() {
        // "member-removed" no longer appears in `members` (as if deleteMember ran), but its old
        // completion_participants rows survive - completions still reference it.
        val completions = listOf(
            completion(
                id = "completion-1",
                choreId = "chore-dishes",
                createdAt = "2026-03-28T18:00:00Z",
                participantMemberIds = listOf("member-alice"),
            ),
            completion(
                id = "completion-2",
                choreId = "chore-dishes",
                createdAt = "2026-03-27T18:00:00Z",
                participantMemberIds = listOf("member-bob"),
            ),
            completion(
                id = "completion-3",
                choreId = "chore-dishes",
                createdAt = "2026-03-26T18:00:00Z",
                participantMemberIds = listOf("member-removed"),
            ),
        )

        val dashboard = calculator.dashboardSnapshot(
            household = household,
            members = members,
            chores = chores,
            completions = completions,
            timeZone = timeZone,
            today = today,
        )

        val contributions = dashboard.memberContributions.associateBy { it.displayName }
        assertThat(contributions["Alice"]?.sharePercent).isEqualTo(50)
        assertThat(contributions["Bob"]?.sharePercent).isEqualTo(50)
    }

    @Test
    fun `buildStaleness excludes paused chores so they don't trigger reminders`() {
        val pausedChore = Chore(
            id = "chore-paused",
            householdId = household.id,
            name = "Paused chore",
            isActive = false,
            createdAt = Instant.parse("2026-01-05T09:00:00Z"),
        )

        val staleness = calculator.buildStaleness(
            chores = chores + pausedChore,
            completions = emptyList(),
            timeZone = timeZone,
            today = today,
        )

        assertThat(staleness.map { it.choreName }).doesNotContain("Paused chore")
    }

    @Test
    fun `stats comparison handles ties and monthly breakdown stays sorted`() {
        val completions = listOf(
            completion(
                id = "completion-1",
                choreId = "chore-dishes",
                createdAt = "2026-03-28T18:00:00Z",
                participantMemberIds = listOf("member-alice"),
            ),
            completion(
                id = "completion-2",
                choreId = "chore-dishes",
                createdAt = "2026-03-24T18:00:00Z",
                participantMemberIds = listOf("member-bob"),
            ),
            completion(
                id = "completion-3",
                choreId = "chore-vacuum",
                createdAt = "2026-02-11T18:00:00Z",
                participantMemberIds = listOf("member-bob"),
            ),
        )

        val stats = calculator.statsSnapshot(
            household = household,
            members = members,
            chores = chores,
            completions = completions,
            timeZone = timeZone,
            today = today,
        )

        val comparisons = stats.comparisons.associateBy { it.choreName }
        assertThat(comparisons["Dishes"]?.leader).isEqualTo(ChoreLeaderResult.Tie)
        assertThat(comparisons["Vacuum"]?.leader).isEqualTo(ChoreLeaderResult.Leader("Bob"))
        assertThat(comparisons["Dusting"]?.leader).isEqualTo(ChoreLeaderResult.NoData)

        // Six contiguous calendar months ending at `today`'s month (2026-03), zero-filled - not
        // just the two months that happen to have a completion.
        assertThat(stats.monthlyBreakdown.map { it.monthLabel })
            .containsExactly("2026-03", "2026-02", "2026-01", "2025-12", "2025-11", "2025-10").inOrder()
        assertThat(stats.monthlyBreakdown.first().countsByMemberId["member-alice"]).isEqualTo(1)
        assertThat(stats.monthlyBreakdown.first().countsByMemberId["member-bob"]).isEqualTo(1)
        assertThat(stats.monthlyBreakdown.first().totalCount).isEqualTo(2)
        assertThat(stats.monthlyBreakdown[1].totalCount).isEqualTo(1)
        assertThat(stats.monthlyBreakdown[2].totalCount).isEqualTo(0)

        // 3 completions, Alice has 1, Bob has 2 → 33% and 66%
        val contributions = stats.summary
        assertThat(contributions.totalCompletions).isEqualTo(3)
        assertThat(contributions.topContributor).isEqualTo(TopContributorResult.Leader("Bob", 66))
        val contribByName = stats.memberContributions.associateBy { it.displayName }
        assertThat(contribByName["Alice"]?.sharePercent).isEqualTo(33)
        assertThat(contribByName["Bob"]?.sharePercent).isEqualTo(66)
    }

    @Test
    fun `countsByMemberId keeps two members with the same display name separate`() {
        val duplicateNameMembers = members + HouseholdMember(
            id = "member-carol",
            householdId = household.id,
            userId = "user-carol",
            displayName = "Bob",
            role = HouseholdRole.MEMBER,
        )
        val completions = listOf(
            completion(
                id = "completion-1",
                choreId = "chore-dishes",
                createdAt = "2026-03-28T18:00:00Z",
                participantMemberIds = listOf("member-bob"),
            ),
            completion(
                id = "completion-2",
                choreId = "chore-dishes",
                createdAt = "2026-03-27T18:00:00Z",
                participantMemberIds = listOf("member-carol"),
            ),
            completion(
                id = "completion-3",
                choreId = "chore-dishes",
                createdAt = "2026-03-26T18:00:00Z",
                participantMemberIds = listOf("member-carol"),
            ),
        )

        val stats = calculator.statsSnapshot(
            household = household,
            members = duplicateNameMembers,
            chores = chores,
            completions = completions,
            timeZone = timeZone,
            today = today,
        )

        val dishes = stats.comparisons.first { it.choreName == "Dishes" }
        assertThat(dishes.countsByMemberId["member-bob"]).isEqualTo(1)
        assertThat(dishes.countsByMemberId["member-carol"]).isEqualTo(2)
        assertThat(dishes.leader).isEqualTo(ChoreLeaderResult.Leader("Bob"))
    }

    @Test
    fun `staleness uses per-chore frequency when set`() {
        val choresWithFrequency = listOf(
            Chore(
                id = "chore-laundry",
                householdId = household.id,
                name = "Laundry",
                isActive = true,
                createdAt = Instant.parse("2026-01-01T09:00:00Z"),
                frequencyDays = 3,
            ),
            Chore(
                id = "chore-oven",
                householdId = household.id,
                name = "Oven",
                isActive = true,
                createdAt = Instant.parse("2026-01-01T09:00:00Z"),
                frequencyDays = 60,
            ),
        )
        val completions = listOf(
            completion(
                id = "c1",
                choreId = "chore-laundry",
                createdAt = "2026-03-27T12:00:00Z", // 2 days ago → 2/3 = 66% >= 80% → "Soon"
                participantMemberIds = listOf("member-alice"),
            ),
            completion(
                id = "c2",
                choreId = "chore-oven",
                createdAt = "2026-03-15T12:00:00Z", // 14 days ago → 14/60 = 23% < 80% → "OK"
                participantMemberIds = listOf("member-alice"),
            ),
        )

        val dashboard = calculator.dashboardSnapshot(
            household = household,
            members = members,
            chores = choresWithFrequency,
            completions = completions,
            timeZone = timeZone,
            today = today,
        )

        val staleness = dashboard.staleChores.associateBy { it.choreName }
        assertThat(staleness["Laundry"]?.status).isEqualTo(ChoreStatus.SOON)
        assertThat(staleness["Laundry"]?.frequencyDays).isEqualTo(3)
        assertThat(staleness["Oven"]?.status).isEqualTo(ChoreStatus.OK)
        assertThat(staleness["Oven"]?.frequencyDays).isEqualTo(60)

        // laundry last done 2 days ago, frequency 3 days → overdue test: 4 days ago
        val overdueCompletions = listOf(
            completion(
                id = "c3",
                choreId = "chore-laundry",
                createdAt = "2026-03-25T12:00:00Z", // 4 days ago > 3 day frequency → "Needs attention"
                participantMemberIds = listOf("member-alice"),
            ),
        )
        val dashboard2 = calculator.dashboardSnapshot(
            household = household,
            members = members,
            chores = choresWithFrequency,
            completions = overdueCompletions,
            timeZone = timeZone,
            today = today,
        )
        assertThat(dashboard2.staleChores.first { it.choreName == "Laundry" }.status)
            .isEqualTo(ChoreStatus.NEEDS_ATTENTION)
    }

    @Test
    fun `SOON is still reachable for a chore due every 1-2 days`() {
        val everyOtherDayChore = Chore(
            id = "chore-plants",
            householdId = household.id,
            name = "Water plants",
            isActive = true,
            createdAt = Instant.parse("2026-01-01T09:00:00Z"),
            frequencyDays = 2,
        )
        val completions = listOf(
            completion(
                id = "c1",
                choreId = "chore-plants",
                createdAt = "2026-03-28T12:00:00Z", // 1 day ago, frequency 2 → SOON, not NEEDS_ATTENTION
                participantMemberIds = listOf("member-alice"),
            ),
        )

        val dashboard = calculator.dashboardSnapshot(
            household = household,
            members = members,
            chores = listOf(everyOtherDayChore),
            completions = completions,
            timeZone = timeZone,
            today = today,
        )

        assertThat(dashboard.staleChores.single().status).isEqualTo(ChoreStatus.SOON)
    }

    private fun completion(
        id: String,
        choreId: String,
        createdAt: String,
        participantMemberIds: List<String>,
    ) = ChoreCompletion(
        id = id,
        householdId = household.id,
        choreId = choreId,
        createdAt = Instant.parse(createdAt),
        createdByUserId = "user-alice",
        note = null,
        participantMemberIds = participantMemberIds,
    )
}
