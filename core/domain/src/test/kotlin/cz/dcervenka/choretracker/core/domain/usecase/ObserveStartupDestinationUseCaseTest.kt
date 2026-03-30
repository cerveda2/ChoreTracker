package cz.dcervenka.choretracker.core.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.model.app.StartupDestination
import cz.dcervenka.choretracker.core.test.mock.sampleAuthenticatedState
import cz.dcervenka.choretracker.core.test.mock.sampleHousehold
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test

class ObserveStartupDestinationUseCaseTest {

    @MockK
    lateinit var authRepository: AuthRepository

    @MockK
    lateinit var householdRepository: HouseholdRepository

    private val authStateFlow = MutableStateFlow<cz.dcervenka.choretracker.core.model.auth.AuthState>(
        sampleAuthenticatedState(),
    )
    private val householdFlow = MutableStateFlow<cz.dcervenka.choretracker.core.model.household.Household?>(
        sampleHousehold(),
    )
    private lateinit var useCase: ObserveStartupDestinationUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        authStateFlow.value = cz.dcervenka.choretracker.core.model.auth.AuthState.SignedOut
        householdFlow.value = null
        every { authRepository.authState } returns authStateFlow
        every { householdRepository.observeCurrentHousehold() } returns householdFlow
        useCase = ObserveStartupDestinationUseCase(
            authRepository = authRepository,
            householdRepository = householdRepository,
        )
    }

    @Test
    fun `emits auth then onboarding then main as session becomes ready`() = runTest {
        useCase().test {
            assertThat(awaitItem()).isEqualTo(StartupDestination.AUTH)

            authStateFlow.value = sampleAuthenticatedState()
            assertThat(awaitItem()).isEqualTo(StartupDestination.ONBOARDING)

            householdFlow.value = sampleHousehold()
            assertThat(awaitItem()).isEqualTo(StartupDestination.MAIN)
        }
    }
}
