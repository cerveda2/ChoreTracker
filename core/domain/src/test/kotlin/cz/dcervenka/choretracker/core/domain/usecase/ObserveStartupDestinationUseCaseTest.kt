package cz.dcervenka.choretracker.core.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.model.app.StartupDestination
import cz.dcervenka.choretracker.core.model.household.HouseholdRestoreStatus
import cz.dcervenka.choretracker.core.test.mock.sampleAuthenticatedState
import cz.dcervenka.choretracker.core.test.mock.sampleHousehold
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
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
    private val restoreStatusFlow = MutableStateFlow(HouseholdRestoreStatus())
    private lateinit var useCase: ObserveStartupDestinationUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        authStateFlow.value = cz.dcervenka.choretracker.core.model.auth.AuthState.Initializing
        householdFlow.value = null
        restoreStatusFlow.value = HouseholdRestoreStatus()
        every { authRepository.authState } returns authStateFlow
        every { householdRepository.observeCurrentHousehold() } returns householdFlow
        every { householdRepository.observeRestoreStatus() } returns restoreStatusFlow
        useCase = ObserveStartupDestinationUseCase(
            authRepository = authRepository,
            householdRepository = householdRepository,
        )
    }

    @Test
    fun `emits nothing while initializing then auth when signed out`() = runTest {
        useCase().test {
            expectNoEvents()

            authStateFlow.value = cz.dcervenka.choretracker.core.model.auth.AuthState.SignedOut
            assertThat(awaitItem()).isEqualTo(StartupDestination.AUTH)
        }
    }

    @Test
    fun `emits main directly when session restores from initializing`() = runTest {
        useCase().test {
            expectNoEvents()

            restoreStatusFlow.value = HouseholdRestoreStatus(isRestoring = true)
            authStateFlow.value = sampleAuthenticatedState()
            householdFlow.value = sampleHousehold()
            restoreStatusFlow.value = HouseholdRestoreStatus(isRestoring = false)
            assertThat(awaitItem()).isEqualTo(StartupDestination.MAIN)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `waits for restore to finish before emitting onboarding for authenticated user`() = runTest {
        useCase().test {
            expectNoEvents()

            restoreStatusFlow.value = HouseholdRestoreStatus(isRestoring = true)
            authStateFlow.value = sampleAuthenticatedState()
            expectNoEvents()

            restoreStatusFlow.value = HouseholdRestoreStatus(isRestoring = false)
            assertThat(awaitItem()).isEqualTo(StartupDestination.ONBOARDING)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
