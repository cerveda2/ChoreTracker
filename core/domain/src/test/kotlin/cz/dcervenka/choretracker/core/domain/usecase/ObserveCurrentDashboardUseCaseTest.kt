package cz.dcervenka.choretracker.core.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.data.contract.StatsRepository
import cz.dcervenka.choretracker.core.domain.HouseholdStatisticsCalculator
import cz.dcervenka.choretracker.core.model.stats.ChoreStaleness
import cz.dcervenka.choretracker.core.model.stats.ChoreStatus
import cz.dcervenka.choretracker.core.model.stats.HouseholdStatsInput
import cz.dcervenka.choretracker.core.test.clock.FixedClock
import cz.dcervenka.choretracker.core.test.mock.sampleChore
import cz.dcervenka.choretracker.core.test.mock.sampleDashboardSnapshot
import cz.dcervenka.choretracker.core.test.mock.sampleHousehold
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

class ObserveCurrentDashboardUseCaseTest {

    @MockK
    lateinit var householdRepository: HouseholdRepository

    @MockK
    lateinit var statsRepository: StatsRepository

    @MockK
    lateinit var statisticsCalculator: HouseholdStatisticsCalculator

    @MockK
    lateinit var observePersonalChoresUseCase: ObservePersonalChoresUseCase

    @MockK
    lateinit var observePersonalStaleChoresUseCase: ObservePersonalStaleChoresUseCase

    private val householdFlow = MutableStateFlow<cz.dcervenka.choretracker.core.model.household.Household?>(null)

    // Derived the same way production code derives `today`, so this stays correct regardless
    // of the test runner's default timezone.
    private val fixedInstant = Instant.parse("2026-03-15T12:00:00Z")
    private val clock = FixedClock(fixedInstant)
    private val expectedToday = fixedInstant.toLocalDateTime(TimeZone.currentSystemDefault()).date

    private lateinit var useCase: ObserveCurrentDashboardUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        householdFlow.value = null
        every { householdRepository.observeCurrentHousehold() } returns householdFlow
        every { observePersonalChoresUseCase() } returns flowOf(emptyList())
        every { observePersonalStaleChoresUseCase() } returns flowOf(emptyList())
        useCase = ObserveCurrentDashboardUseCase(
            householdRepository = householdRepository,
            statsRepository = statsRepository,
            statisticsCalculator = statisticsCalculator,
            observePersonalChoresUseCase = observePersonalChoresUseCase,
            observePersonalStaleChoresUseCase = observePersonalStaleChoresUseCase,
            clock = clock,
        )
    }

    @Test
    fun `starts observing dashboard only after a household is available`() = runTest {
        val household = sampleHousehold(id = "household-42")
        val input = HouseholdStatsInput(
            household = household,
            members = emptyList(),
            chores = emptyList(),
            completions = emptyList(),
        )
        val snapshot = sampleDashboardSnapshot().copy(household = household)
        every { statsRepository.observeHouseholdStatsInput(household.id) } returns MutableStateFlow(input)
        every {
            statisticsCalculator.dashboardSnapshot(
                household = input.household,
                members = input.members,
                chores = input.chores,
                completions = input.completions,
                today = expectedToday,
            )
        } returns snapshot

        useCase().test {
            householdFlow.value = household

            assertThat(awaitItem()).isEqualTo(snapshot)
        }
    }

    @Test
    fun `uses the current household rather than the stats input's placeholder`() = runTest {
        val household = sampleHousehold(id = "preview-household").copy(name = "Sunny Flat")
        val input = HouseholdStatsInput(
            household = household.copy(name = "Household"),
            members = emptyList(),
            chores = emptyList(),
            completions = emptyList(),
        )
        val snapshot = sampleDashboardSnapshot().copy(household = household)
        every { statsRepository.observeHouseholdStatsInput(household.id) } returns MutableStateFlow(input)
        every {
            statisticsCalculator.dashboardSnapshot(
                household = household,
                members = input.members,
                chores = input.chores,
                completions = input.completions,
                today = expectedToday,
            )
        } returns snapshot

        useCase().test {
            householdFlow.value = household

            assertThat(awaitItem().household.name).isEqualTo("Sunny Flat")
        }
    }

    @Test
    fun `personal chores join the due lists but not the household's numbers`() = runTest {
        val household = sampleHousehold(id = "household-42")
        val input = HouseholdStatsInput(
            household = household,
            members = emptyList(),
            chores = emptyList(),
            completions = emptyList(),
        )
        val snapshot = sampleDashboardSnapshot()
            .copy(household = household, activeChores = emptyList(), staleChores = emptyList())
        val gym = sampleChore().copy(id = "gym", name = "Gym", householdId = "", isPersonal = true)
        val paused = gym.copy(id = "paused", isActive = false)
        val gymStaleness = ChoreStaleness(
            choreId = "gym",
            choreName = "Gym",
            lastCompletedDate = null,
            daysSinceLastCompletion = null,
            schedule = null,
            status = ChoreStatus.NEVER,
        )
        every { statsRepository.observeHouseholdStatsInput(household.id) } returns MutableStateFlow(input)
        every { statisticsCalculator.dashboardSnapshot(any(), any(), any(), any(), any(), any(), any()) } returns snapshot
        every { observePersonalChoresUseCase() } returns flowOf(listOf(gym, paused))
        every { observePersonalStaleChoresUseCase() } returns flowOf(listOf(gymStaleness))

        useCase().test {
            householdFlow.value = household

            val result = awaitItem()
            assertThat(result.activeChores.map { it.id }).containsExactly("gym")
            assertThat(result.staleChores).containsExactly(gymStaleness)
            assertThat(result.summary).isEqualTo(snapshot.summary)
            assertThat(result.balance).isEqualTo(snapshot.balance)
        }
    }
}
