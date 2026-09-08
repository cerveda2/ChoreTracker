package cz.dcervenka.choretracker.core.domain

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.chore.ChoreCompletion
import cz.dcervenka.choretracker.core.model.household.Household
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import cz.dcervenka.choretracker.core.model.stats.ChoreLeaderResult
import cz.dcervenka.choretracker.core.model.stats.StatsPeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Test
import kotlin.time.Instant

// Split out of HouseholdStatisticsCalculatorTest (which was tripping detekt's LargeClass) - covers
// the redesign Phase 5 additions: ShareBreakdown, ChoreComparison.nextTurnMemberId, and
// StatsPeriod filtering. Same fixtures, deliberately duplicated rather than shared, so each test
// file stays self-contained and readable on its own.
class HouseholdStatisticsCalculatorSharesTest {

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
    fun `share breakdown percentages sum to 100 across current members even after a member is removed`() {
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

        val stats = calculator.statsSnapshot(
            household = household,
            members = members,
            chores = chores,
            completions = completions,
            period = StatsPeriod.ALL,
            timeZone = timeZone,
            today = today,
        )

        // The removed member's completion is excluded entirely, not counted in the denominator -
        // otherwise the remaining two members' shares could never add up to 100.
        assertThat(stats.shareBreakdown.totalCount).isEqualTo(2)
        assertThat(stats.shareBreakdown.percentByMemberId["member-alice"]).isEqualTo(50)
        assertThat(stats.shareBreakdown.percentByMemberId["member-bob"]).isEqualTo(50)
        assertThat(
            stats.shareBreakdown.percentByMemberId.values.sum() + stats.shareBreakdown.togetherPercent,
        ).isEqualTo(100)
    }

    @Test
    fun `share breakdown credits a shared completion to Together, not either participant`() {
        val completions = listOf(
            completion(
                id = "completion-shared",
                choreId = "chore-dishes",
                createdAt = "2026-03-28T18:00:00Z",
                participantMemberIds = listOf("member-alice", "member-bob"),
            ),
        )

        val stats = calculator.statsSnapshot(
            household = household,
            members = members,
            chores = chores,
            completions = completions,
            period = StatsPeriod.ALL,
            timeZone = timeZone,
            today = today,
        )

        assertThat(stats.shareBreakdown.sharedCount).isEqualTo(1)
        assertThat(stats.shareBreakdown.togetherPercent).isEqualTo(100)
        assertThat(stats.shareBreakdown.soloCountByMemberId.values).containsExactly(0, 0)
        assertThat(stats.shareBreakdown.percentByMemberId["member-alice"]).isEqualTo(0)
        assertThat(stats.shareBreakdown.percentByMemberId["member-bob"]).isEqualTo(0)
    }

    @Test
    fun `share breakdown percentages sum to exactly 100 with a 33-33-34 style split`() {
        val threeMembers = members + HouseholdMember(
            id = "member-carol",
            householdId = household.id,
            userId = "user-carol",
            displayName = "Carol",
            role = HouseholdRole.MEMBER,
        )
        val completions = listOf("member-alice", "member-bob", "member-carol").map { memberId ->
            completion(
                id = "completion-$memberId",
                choreId = "chore-dishes",
                createdAt = "2026-03-28T18:00:00Z",
                participantMemberIds = listOf(memberId),
            )
        }

        val stats = calculator.statsSnapshot(
            household = household,
            members = threeMembers,
            chores = chores,
            completions = completions,
            period = StatsPeriod.ALL,
            timeZone = timeZone,
            today = today,
        )

        assertThat(stats.shareBreakdown.percentByMemberId.values.sum() + stats.shareBreakdown.togetherPercent)
            .isEqualTo(100)
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
            period = StatsPeriod.ALL,
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

        // 3 completions, Alice has 1, Bob has 2 - largest-remainder rounding gives 33/67 (not the
        // naive 33/66, which sums to 99).
        assertThat(stats.summary.totalCompletions).isEqualTo(3)
        assertThat(stats.shareBreakdown.percentByMemberId["member-alice"]).isEqualTo(33)
        assertThat(stats.shareBreakdown.percentByMemberId["member-bob"]).isEqualTo(67)
    }

    @Test
    fun `next turn picks the member with the lowest count`() {
        val completions = listOf(
            completion(
                id = "c1",
                choreId = "chore-dishes",
                createdAt = "2026-03-20T12:00:00Z",
                participantMemberIds = listOf("member-alice"),
            ),
            completion(
                id = "c2",
                choreId = "chore-dishes",
                createdAt = "2026-03-21T12:00:00Z",
                participantMemberIds = listOf("member-alice"),
            ),
        )

        val stats = calculator.statsSnapshot(
            household = household,
            members = members,
            chores = chores,
            completions = completions,
            period = StatsPeriod.ALL,
            timeZone = timeZone,
            today = today,
        )

        val dishes = stats.comparisons.first { it.choreName == "Dishes" }
        assertThat(dishes.nextTurnMemberId).isEqualTo("member-bob")
    }

    @Test
    fun `next turn breaks a tie away from whoever completed it last`() {
        val completions = listOf(
            completion(
                id = "c1",
                choreId = "chore-dishes",
                createdAt = "2026-03-20T12:00:00Z",
                participantMemberIds = listOf("member-alice"),
            ),
            completion(
                id = "c2",
                choreId = "chore-dishes",
                createdAt = "2026-03-27T12:00:00Z",
                participantMemberIds = listOf("member-bob"),
            ),
        )

        val stats = calculator.statsSnapshot(
            household = household,
            members = members,
            chores = chores,
            completions = completions,
            period = StatsPeriod.ALL,
            timeZone = timeZone,
            today = today,
        )

        // Both have done it once - tied on count - but Bob did it most recently, so it's Alice's turn.
        val dishes = stats.comparisons.first { it.choreName == "Dishes" }
        assertThat(dishes.nextTurnMemberId).isEqualTo("member-alice")
    }

    @Test
    fun `next turn is null when the tie survives the last-completer tie-break`() {
        // Nobody has ever done it - both tied at 0, and there's no "last completer" to break the
        // tie away from.
        val stats = calculator.statsSnapshot(
            household = household,
            members = members,
            chores = chores,
            completions = emptyList(),
            period = StatsPeriod.ALL,
            timeZone = timeZone,
            today = today,
        )

        val dishes = stats.comparisons.first { it.choreName == "Dishes" }
        assertThat(dishes.nextTurnMemberId).isNull()
    }

    @Test
    fun `period WEEK only counts completions from the trailing 7 days for shares and by-chore rows`() {
        val completions = listOf(
            // Exactly 6 days ago - the oldest day still inside a 7-day window.
            completion(
                id = "in-window",
                choreId = "chore-dishes",
                createdAt = "2026-03-23T12:00:00Z",
                participantMemberIds = listOf("member-alice"),
            ),
            // One day older - outside the window.
            completion(
                id = "out-of-window",
                choreId = "chore-vacuum",
                createdAt = "2026-03-22T12:00:00Z",
                participantMemberIds = listOf("member-bob"),
            ),
        )

        val stats = calculator.statsSnapshot(
            household = household,
            members = members,
            chores = chores,
            completions = completions,
            period = StatsPeriod.WEEK,
            timeZone = timeZone,
            today = today,
        )

        assertThat(stats.summary.totalCompletions).isEqualTo(1)
        assertThat(stats.shareBreakdown.totalCount).isEqualTo(1)
        assertThat(stats.comparisons.first { it.choreName == "Vacuum" }.totalCount).isEqualTo(0)
        assertThat(stats.comparisons.first { it.choreName == "Dishes" }.totalCount).isEqualTo(1)
    }

    @Test
    fun `period filtering leaves monthly breakdown and staleness unaffected`() {
        val completions = listOf(
            completion(
                id = "old",
                choreId = "chore-dishes",
                createdAt = "2026-02-01T12:00:00Z",
                participantMemberIds = listOf("member-alice"),
            ),
        )

        val stats = calculator.statsSnapshot(
            household = household,
            members = members,
            chores = chores,
            completions = completions,
            period = StatsPeriod.WEEK,
            timeZone = timeZone,
            today = today,
        )

        // Excluded from the week-scoped totals...
        assertThat(stats.shareBreakdown.totalCount).isEqualTo(0)
        // ...but still visible in the fixed 6-month trend and in the chore's true staleness.
        assertThat(stats.monthlyBreakdown.sumOf { it.totalCount }).isEqualTo(1)
        assertThat(stats.staleChores.first { it.choreName == "Dishes" }.daysSinceLastCompletion).isEqualTo(56)
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
