package cz.dcervenka.choretracker.core.domain

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.chore.ChoreCompletion
import cz.dcervenka.choretracker.core.model.household.Household
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import cz.dcervenka.choretracker.core.model.stats.ChoreLeaderResult
import cz.dcervenka.choretracker.core.model.stats.ChoreStatus
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
        assertThat(dashboard.summary.totalCompletions).isEqualTo(4)
        assertThat(dashboard.summary.topContributor?.displayName).isAnyOf("Alice", "Bob")

        assertThat(dashboard.recentCompletions.first().participantNames).containsExactly("Alice", "Bob").inOrder()

        val staleness = dashboard.staleChores.associateBy { it.choreName }
        assertThat(staleness["Dishes"]?.status).isEqualTo(ChoreStatus.OK)
        assertThat(staleness["Vacuum"]?.status).isEqualTo(ChoreStatus.NEEDS_ATTENTION)
        assertThat(staleness["Dusting"]?.status).isEqualTo(ChoreStatus.NEVER)
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

        assertThat(stats.monthlyBreakdown.map { it.monthLabel }).containsExactly("2026-03", "2026-02").inOrder()
        assertThat(stats.monthlyBreakdown.first().countsByMember["Alice"]).isEqualTo(1)
        assertThat(stats.monthlyBreakdown.first().countsByMember["Bob"]).isEqualTo(1)
        assertThat(stats.monthlyBreakdown.first().totalCount).isEqualTo(2)

        // 3 completions, Alice has 1, Bob has 2 → 33% and 66%
        val contributions = stats.summary
        assertThat(contributions.totalCompletions).isEqualTo(3)
        assertThat(contributions.topContributor?.displayName).isEqualTo("Bob")
        val contribByName = stats.memberContributions.associateBy { it.displayName }
        assertThat(contribByName["Alice"]?.sharePercent).isEqualTo(33)
        assertThat(contribByName["Bob"]?.sharePercent).isEqualTo(66)
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
