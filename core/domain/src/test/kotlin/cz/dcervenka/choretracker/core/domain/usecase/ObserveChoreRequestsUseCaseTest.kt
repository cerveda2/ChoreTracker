package cz.dcervenka.choretracker.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.data.contract.ChoreRequestRepository
import cz.dcervenka.choretracker.core.data.contract.StatsRepository
import cz.dcervenka.choretracker.core.model.chore.ChoreCompletion
import cz.dcervenka.choretracker.core.model.chore.ChoreRequest
import cz.dcervenka.choretracker.core.model.chore.ChoreRequestState
import cz.dcervenka.choretracker.core.model.stats.HouseholdStatsInput
import cz.dcervenka.choretracker.core.test.clock.FixedClock
import cz.dcervenka.choretracker.core.test.mock.sampleChore
import cz.dcervenka.choretracker.core.test.mock.sampleHousehold
import cz.dcervenka.choretracker.core.test.mock.sampleMembers
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Instant

class ObserveChoreRequestsUseCaseTest {

    private val now = Instant.parse("2026-03-10T12:00:00Z")

    private fun request(
        id: String,
        from: String = "member-2",
        to: String = "member-1",
        state: ChoreRequestState = ChoreRequestState.OPEN,
        resolvedAt: Instant? = null,
    ) = ChoreRequest(
        id = id,
        householdId = "household-1",
        batchId = "batch-$id",
        choreId = "chore-1",
        fromMemberId = from,
        toMemberId = to,
        toUserId = "user-x",
        createdByUserId = "user-y",
        note = "Before dinner",
        createdAt = Instant.parse("2026-03-10T08:00:00Z"),
        state = state,
        resolvedAt = resolvedAt,
    )

    private fun observe(requests: List<ChoreRequest>, completions: List<ChoreCompletion> = emptyList()) = run {
        val repository = mockk<ChoreRequestRepository> {
            every { observeRequests("household-1") } returns flowOf(requests)
        }
        val stats = mockk<StatsRepository> {
            every { observeHouseholdStatsInput("household-1") } returns flowOf(
                HouseholdStatsInput(sampleHousehold(), sampleMembers(), listOf(sampleChore()), completions),
            )
        }
        ObserveChoreRequestsUseCase(repository, stats, FixedClock(now))("household-1")
    }

    @Test
    fun `splits open requests into asked of me and waiting on others`() = runTest {
        val result = observe(listOf(request("in"), request("out", from = "member-1", to = "member-2"))).first()

        assertThat(result.askedOfMe.map { it.id }).containsExactly("in")
        assertThat(result.askedOfMe.single().otherMemberName).isEqualTo("Alex")
        assertThat(result.askedOfMe.single().choreName).isEqualTo("Kitchen")
        assertThat(result.waitingOn.map { it.id }).containsExactly("out")
        assertThat(result.waitingOn.single().otherMemberName).isEqualTo("Alex")
    }

    @Test
    fun `a request drops out once anyone logs its chore after it was sent`() = runTest {
        val logged = ChoreCompletion(
            id = "c1",
            householdId = "household-1",
            choreId = "chore-1",
            createdAt = Instant.parse("2026-03-10T09:00:00Z"),
            createdByUserId = "user-2",
            note = null,
            participantMemberIds = listOf("member-2"),
        )

        val before = logged.copy(id = "old", createdAt = Instant.parse("2026-03-09T09:00:00Z"))

        assertThat(observe(listOf(request("in")), listOf(before)).first().askedOfMe).hasSize(1)
        val result = observe(listOf(request("in")), listOf(logged, before)).first()

        assertThat(result.askedOfMe).isEmpty()
    }

    @Test
    fun `the sender sees a decline for a day, and nobody sees a cancelled one`() = runTest {
        val result = observe(
            listOf(
                request(
                    "fresh",
                    from = "member-1",
                    to = "member-2",
                    state = ChoreRequestState.DECLINED,
                    resolvedAt = now,
                ),
                request(
                    "stale",
                    from = "member-1",
                    to = "member-2",
                    state = ChoreRequestState.DECLINED,
                    resolvedAt = Instant.parse("2026-03-09T11:00:00Z"),
                ),
                request("cancelled", state = ChoreRequestState.CANCELLED, resolvedAt = now),
                request("declined-by-me", state = ChoreRequestState.DECLINED, resolvedAt = now),
            ),
        ).first()

        assertThat(result.waitingOn.map { it.id to it.declined }).containsExactly("fresh" to true)
        assertThat(result.askedOfMe).isEmpty()
    }
}
