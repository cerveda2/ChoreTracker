package cz.dcervenka.choretracker.core.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.data.contract.StatsRepository
import cz.dcervenka.choretracker.core.test.mock.sampleDashboardSnapshot
import cz.dcervenka.choretracker.core.test.mock.sampleHousehold
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ObserveCurrentDashboardUseCaseTest {

    @MockK
    lateinit var householdRepository: HouseholdRepository

    @MockK
    lateinit var statsRepository: StatsRepository

    private val householdFlow = MutableStateFlow<cz.dcervenka.choretracker.core.model.household.Household?>(null)
    private lateinit var useCase: ObserveCurrentDashboardUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        householdFlow.value = null
        every { householdRepository.observeCurrentHousehold() } returns householdFlow
        useCase = ObserveCurrentDashboardUseCase(
            householdRepository = householdRepository,
            statsRepository = statsRepository,
        )
    }

    @Test
    fun `starts observing dashboard only after a household is available`() = runTest {
        val household = sampleHousehold(id = "household-42")
        val snapshot = sampleDashboardSnapshot().copy(household = household)
        every { statsRepository.observeDashboard(household.id) } returns MutableStateFlow(snapshot)

        useCase().test {
            householdFlow.value = household

            assertThat(awaitItem()).isEqualTo(snapshot)
        }
    }
}
