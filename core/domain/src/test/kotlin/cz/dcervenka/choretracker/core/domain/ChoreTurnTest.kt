package cz.dcervenka.choretracker.core.domain

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.model.chore.Chore
import cz.dcervenka.choretracker.core.model.chore.ChoreCompletion
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import cz.dcervenka.choretracker.core.model.stats.ChoreTurn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Test
import kotlin.time.Instant

class ChoreTurnTest {

    private val calculator = HouseholdStatisticsCalculator()

    private val alice = member("member-alice", "Alice", isCurrentUser = true)
    private val bob = member("member-bob", "Bob")

    private val dishes = Chore(
        id = "chore-dishes",
        householdId = "household-1",
        name = "Dishes",
        isActive = true,
        createdAt = Instant.parse("2026-01-02T09:00:00Z"),
        rotationMemberIds = listOf(alice.id, bob.id),
        rotationStartedAt = Instant.parse("2026-03-01T00:00:00Z"),
    )

    @Test
    fun `whose turn goes to whoever has done it least since the rotation started`() {
        val beforeRotation = completion("c0", "2026-02-20T18:00:00Z", bob)
        val aliceFirst = completion("c1", "2026-03-10T18:00:00Z", alice)
        val bobNext = completion("c2", "2026-03-12T18:00:00Z", bob)
        val aliceCovers = completion("c3", "2026-03-14T18:00:00Z", alice)
        val bobCatchesUp = completion("c4", "2026-03-16T18:00:00Z", bob)

        assertThat(turn(dishes, listOf(beforeRotation, aliceFirst, bobNext, aliceCovers))?.memberName)
            .isEqualTo("Bob")
        assertThat(turn(dishes, listOf(beforeRotation, aliceFirst, bobNext, aliceCovers, bobCatchesUp)))
            .isEqualTo(ChoreTurn(memberId = alice.id, memberName = "Alice", isCurrentUser = true))
    }

    @Test
    fun `a shared completion counts for everyone in the rotation who took part`() {
        val together = ChoreCompletion(
            id = "c1",
            householdId = "household-1",
            choreId = dishes.id,
            createdAt = Instant.parse("2026-03-10T18:00:00Z"),
            createdByUserId = "user-alice",
            note = null,
            participantMemberIds = listOf(alice.id, bob.id),
        )

        assertThat(turn(dishes, listOf(together))?.memberName).isEqualTo("Alice")
    }

    @Test
    fun `a new rotation starts with its first member still in the household`() {
        val carol = member("member-carol", "Carol").copy(removedAt = Instant.parse("2026-02-01T00:00:00Z"))
        val rotating = dishes.copy(rotationMemberIds = listOf(carol.id, bob.id, alice.id))

        assertThat(turn(rotating, emptyList(), members = listOf(alice, bob, carol))?.memberName).isEqualTo("Bob")
    }

    @Test
    fun `a chore without a rotation has no turn`() {
        assertThat(turn(dishes.copy(rotationMemberIds = emptyList(), rotationStartedAt = null), emptyList())).isNull()
    }

    private fun turn(
        chore: Chore,
        completions: List<ChoreCompletion>,
        members: List<HouseholdMember> = listOf(alice, bob),
    ): ChoreTurn? = calculator.buildStaleness(
        chores = listOf(chore),
        completions = completions,
        members = members,
        timeZone = TimeZone.UTC,
        today = LocalDate(2026, 3, 29),
    ).single().turn

    private fun completion(id: String, createdAt: String, by: HouseholdMember) = ChoreCompletion(
        id = id,
        householdId = "household-1",
        choreId = dishes.id,
        createdAt = Instant.parse(createdAt),
        createdByUserId = by.userId.orEmpty(),
        note = null,
        participantMemberIds = listOf(by.id),
    )

    private fun member(id: String, name: String, isCurrentUser: Boolean = false) = HouseholdMember(
        id = id,
        householdId = "household-1",
        userId = "user-$id",
        displayName = name,
        role = HouseholdRole.MEMBER,
        isCurrentUser = isCurrentUser,
    )
}
