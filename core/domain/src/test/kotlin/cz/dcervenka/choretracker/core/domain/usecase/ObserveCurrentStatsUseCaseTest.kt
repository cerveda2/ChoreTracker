package cz.dcervenka.choretracker.core.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.data.contract.StatsRepository
import cz.dcervenka.choretracker.core.domain.HouseholdStatisticsCalculator
import cz.dcervenka.choretracker.core.model.stats.HouseholdStatsInput
import cz.dcervenka.choretracker.core.test.mock.sampleHousehold
import cz.dcervenka.choretracker.core.test.mock.sampleStatsSnapshot
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ObserveCurrentStatsUseCaseTest {

    @MockK
    lateinit var householdRepository: HouseholdRepository

    @MockK
    lateinit var statsRepository: StatsRepository

    @MockK
    lateinit var statisticsCalculator: HouseholdStatisticsCalculator

    private val householdFlow = MutableStateFlow<cz.dcervenka.choretracker.core.model.household.Household?>(null)
    private lateinit var useCase: ObserveCurrentStatsUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        householdFlow.value = null
        every { householdRepository.observeCurrentHousehold() } returns householdFlow
        useCase = ObserveCurrentStatsUseCase(
            householdRepository = householdRepository,
            statsRepository = statsRepository,
            statisticsCalculator = statisticsCalculator,
        )
    }

    @Test
    fun `observes stats for the current household`() = runTest {
        val household = sampleHousehold(id = "household-77")
        val input = HouseholdStatsInput(
            household = household,
            members = emptyList(),
            chores = emptyList(),
            completions = emptyList(),
        )
        val snapshot = sampleStatsSnapshot().copy(household = household)
        every { statsRepository.observeHouseholdStatsInput(household.id) } returns MutableStateFlow(input)
        every {
            statisticsCalculator.statsSnapshot(
                household = input.household,
                members = input.members,
                chores = input.chores,
                completions = input.completions,
                today = any(),
            )
        } returns snapshot

        useCase().test {
            householdFlow.value = household

            assertThat(awaitItem()).isEqualTo(snapshot)
        }
    }
}
