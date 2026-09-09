package cz.dcervenka.choretracker.core.domain

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.chore.ChoreCompletion
import cz.dcervenka.choretracker.core.model.household.Household
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import cz.dcervenka.choretracker.core.model.stats.ChoreLeaderResult
import cz.dcervenka.choretracker.core.model.stats.ChoreStatus
import cz.dcervenka.choretracker.core.model.stats.StatsPeriod
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
        assertThat(contributions["Bob"]?.totalCount).isEqualTo(2)
        assertThat(contributions["Bob"]?.last30DaysCount).isEqualTo(2)
        // 3 completions total (completion-1 is shared, so it's one completion, not two) -
        // matches the definition ChoreComparison/CategoryComparison/MonthlyBreakdown.totalCount
        // already use, not the sum of each member's own totalCount (which double-counts a shared
        // completion once per participant).
        assertThat(dashboard.summary.totalCompletions).isEqualTo(3)

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
            period = StatsPeriod.ALL,
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
    fun `totalCompletions is zero when nobody has completed anything yet`() {
        val dashboard = calculator.dashboardSnapshot(
            household = household,
            members = members,
            chores = chores,
            completions = emptyList(),
            timeZone = timeZone,
            today = today,
        )

        assertThat(dashboard.summary.totalCompletions).isEqualTo(0)
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
            members = members,
            timeZone = timeZone,
            today = today,
        )

        assertThat(staleness.map { it.choreName }).doesNotContain("Paused chore")
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
            period = StatsPeriod.ALL,
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

    @Test
    fun `balance names the leader and lagging member from last30DaysCount, with a gap`() {
        val completions = (1..8).map { index ->
            completion(
                id = "alice-$index",
                choreId = "chore-dishes",
                createdAt = "2026-03-2${index % 8}T18:00:00Z",
                participantMemberIds = listOf("member-alice"),
            )
        } + (1..3).map { index ->
            completion(
                id = "bob-$index",
                choreId = "chore-vacuum",
                createdAt = "2026-03-0${index}T18:00:00Z",
                participantMemberIds = listOf("member-bob"),
            )
        }

        val dashboard = calculator.dashboardSnapshot(
            household = household,
            members = members,
            chores = chores,
            completions = completions,
            timeZone = timeZone,
            today = today,
        )

        val balance = dashboard.balance
        assertThat(balance).isNotNull()
        assertThat(balance?.leaderMemberId).isEqualTo("member-alice")
        assertThat(balance?.laggingMemberId).isEqualTo("member-bob")
        assertThat(balance?.gap).isEqualTo(5)
        assertThat(balance?.countsByMemberId).containsExactly("member-alice", 8, "member-bob", 3)
    }

    @Test
    fun `balance is null with fewer than two members`() {
        val dashboard = calculator.dashboardSnapshot(
            household = household,
            members = members.take(1),
            chores = chores,
            completions = listOf(
                completion(
                    id = "completion-1",
                    choreId = "chore-dishes",
                    createdAt = "2026-03-28T18:00:00Z",
                    participantMemberIds = listOf("member-alice"),
                ),
            ),
            timeZone = timeZone,
            today = today,
        )

        assertThat(dashboard.balance).isNull()
    }

    @Test
    fun `balance is null when nobody has logged anything in the last 30 days`() {
        val completions = listOf(
            // Well outside the 30-day window used for last30DaysCount.
            completion(
                id = "completion-old",
                choreId = "chore-dishes",
                createdAt = "2025-01-01T18:00:00Z",
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

        assertThat(dashboard.balance).isNull()
    }

    @Test
    fun `an exact tie still produces a balance with a zero gap`() {
        val completions = listOf(
            completion(
                id = "completion-alice",
                choreId = "chore-dishes",
                createdAt = "2026-03-28T18:00:00Z",
                participantMemberIds = listOf("member-alice"),
            ),
            completion(
                id = "completion-bob",
                choreId = "chore-dishes",
                createdAt = "2026-03-27T18:00:00Z",
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

        assertThat(dashboard.balance?.gap).isEqualTo(0)
    }

    @Test
    fun `staleness resolves who completed the chore last and how many days remain`() {
        val choresWithFrequency = listOf(
            Chore(
                id = "chore-laundry",
                householdId = household.id,
                name = "Laundry",
                isActive = true,
                createdAt = Instant.parse("2026-01-01T09:00:00Z"),
                frequencyDays = 6,
            ),
        )
        val completions = listOf(
            completion(
                id = "c1",
                choreId = "chore-laundry",
                createdAt = "2026-03-24T12:00:00Z", // 5 days ago
                participantMemberIds = listOf("member-alice", "member-bob"),
            ),
        )

        val staleness = calculator.buildStaleness(
            chores = choresWithFrequency,
            completions = completions,
            members = members,
            timeZone = timeZone,
            today = today,
        ).single()

        assertThat(staleness.lastCompletedByNames).containsExactly("Alice", "Bob")
        // frequencyDays (6) - daysSinceLastCompletion (5) = 1 day left.
        assertThat(staleness.dueInDays).isEqualTo(1)
    }

    @Test
    fun `staleness leaves dueInDays and lastCompletedByNames empty for a never-done chore`() {
        val staleness = calculator.buildStaleness(
            chores = chores,
            completions = emptyList(),
            members = members,
            timeZone = timeZone,
            today = today,
        )

        assertThat(staleness).isNotEmpty()
        staleness.forEach {
            assertThat(it.dueInDays).isNull()
            assertThat(it.lastCompletedByNames).isEmpty()
        }
    }

    @Test
    fun `lastCompletedByNames drops a participant who is no longer a current member`() {
        val completions = listOf(
            completion(
                id = "c1",
                choreId = "chore-dishes",
                createdAt = "2026-03-28T18:00:00Z",
                participantMemberIds = listOf("member-alice", "member-removed"),
            ),
        )

        val staleness = calculator.buildStaleness(
            chores = chores,
            completions = completions,
            members = members,
            timeZone = timeZone,
            today = today,
        ).first { it.choreName == "Dishes" }

        assertThat(staleness.lastCompletedByNames).containsExactly("Alice")
    }

    @Test
    fun `daily chore is OK right after being logged`() {
        val dailyChore = Chore(
            id = "chore-dishes-daily",
            householdId = household.id,
            name = "Dishes",
            isActive = true,
            createdAt = Instant.parse("2026-01-01T09:00:00Z"),
            frequencyDays = 1,
        )
        val completions = listOf(
            completion(
                id = "c1",
                choreId = "chore-dishes-daily",
                createdAt = "2026-03-29T12:00:00Z", // done today, 0 days ago
                participantMemberIds = listOf("member-alice"),
            ),
        )

        val dashboard = calculator.dashboardSnapshot(
            household = household,
            members = members,
            chores = listOf(dailyChore),
            completions = completions,
            timeZone = timeZone,
            today = today,
        )

        // A daily chore's soonThreshold used to clamp to 0 (attentionThreshold - 1), which made
        // daysSinceLastCompletion >= soonThreshold true from the moment it was logged, so it
        // could never read OK - see HouseholdStatisticsCalculator.computeStatus.
        assertThat(dashboard.staleChores.single().status).isEqualTo(ChoreStatus.OK)
    }

    @Test
    fun `daily chore needs attention the day after it's due, not the same day it's due`() {
        val dailyChore = Chore(
            id = "chore-dishes-daily",
            householdId = household.id,
            name = "Dishes",
            isActive = true,
            createdAt = Instant.parse("2026-01-01T09:00:00Z"),
            frequencyDays = 1,
        )
        val completions = listOf(
            completion(
                id = "c1",
                choreId = "chore-dishes-daily",
                createdAt = "2026-03-28T12:00:00Z", // 1 day ago - due today, per the >= boundary below
                participantMemberIds = listOf("member-alice"),
            ),
        )

        val dashboard = calculator.dashboardSnapshot(
            household = household,
            members = members,
            chores = listOf(dailyChore),
            completions = completions,
            timeZone = timeZone,
            today = today,
        )

        assertThat(dashboard.staleChores.single().status).isEqualTo(ChoreStatus.NEEDS_ATTENTION)
    }

    @Test
    fun `a chore reads needs-attention exactly on its due day, not only after it passes`() {
        // Documents the existing daysSinceLastCompletion >= attentionThreshold boundary as
        // intentional: a chore due "every 3 days" is expected to be done by day 3, so reaching
        // day 3 with nothing logged already counts as needing attention (and triggers the stale
        // chore reminder push), rather than waiting until day 4.
        val everyThreeDaysChore = Chore(
            id = "chore-laundry",
            householdId = household.id,
            name = "Laundry",
            isActive = true,
            createdAt = Instant.parse("2026-01-01T09:00:00Z"),
            frequencyDays = 3,
        )
        val completions = listOf(
            completion(
                id = "c1",
                choreId = "chore-laundry",
                createdAt = "2026-03-26T12:00:00Z", // exactly 3 days ago
                participantMemberIds = listOf("member-alice"),
            ),
        )

        val dashboard = calculator.dashboardSnapshot(
            household = household,
            members = members,
            chores = listOf(everyThreeDaysChore),
            completions = completions,
            timeZone = timeZone,
            today = today,
        )

        assertThat(dashboard.staleChores.single().status).isEqualTo(ChoreStatus.NEEDS_ATTENTION)
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
