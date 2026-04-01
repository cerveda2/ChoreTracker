package cz.dcervenka.choretracker.viewmodel

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.domain.usecase.ObserveStartupDestinationUseCase
import cz.dcervenka.choretracker.core.model.app.StartupDestination
import cz.dcervenka.choretracker.core.test.rule.TestCoroutineRule
import cz.dcervenka.choretracker.navigation.RootDestination
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AppViewModelTest {

    @get:Rule
    val coroutineRule = TestCoroutineRule(startPaused = true)

    @MockK
    lateinit var observeStartupDestinationUseCase: ObserveStartupDestinationUseCase

    private val startupFlow = MutableStateFlow(StartupDestination.AUTH)

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        startupFlow.value = StartupDestination.AUTH
        every { observeStartupDestinationUseCase() } returns startupFlow
    }

    @Test
    fun `maps startup destination flow into root destination`() = runTest(coroutineRule.dispatcher) {
        val viewModel = AppViewModel(observeStartupDestinationUseCase = observeStartupDestinationUseCase)

        assertThat(viewModel.rootDestination.value).isEqualTo(RootDestination.Loading)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.rootDestination.collect {}
        }
        advanceUntilIdle()

        assertThat(viewModel.rootDestination.value).isEqualTo(RootDestination.Auth)

        startupFlow.value = StartupDestination.ONBOARDING
        advanceUntilIdle()
        assertThat(viewModel.rootDestination.value).isEqualTo(RootDestination.Onboarding)

        startupFlow.value = StartupDestination.MAIN
        advanceUntilIdle()
        assertThat(viewModel.rootDestination.value).isEqualTo(RootDestination.Main)
    }
}
