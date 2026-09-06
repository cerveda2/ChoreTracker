package cz.dcervenka.choretracker.core.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.data.contract.StatsRepository
import cz.dcervenka.choretracker.core.domain.HouseholdStatisticsCalculator
import cz.dcervenka.choretracker.core.model.stats.HouseholdStatsInput
import cz.dcervenka.choretracker.core.test.clock.FixedClock
import cz.dcervenka.choretracker.core.test.mock.sampleDashboardSnapshot
import cz.dcervenka.choretracker.core.test.mock.sampleHousehold
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.MutableStateFlow
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
        useCase = ObserveCurrentDashboardUseCase(
            householdRepository = householdRepository,
            statsRepository = statsRepository,
            statisticsCalculator = statisticsCalculator,
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
}
