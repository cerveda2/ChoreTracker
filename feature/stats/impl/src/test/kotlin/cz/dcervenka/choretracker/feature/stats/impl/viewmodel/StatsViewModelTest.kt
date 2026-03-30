package cz.dcervenka.choretracker.feature.stats.impl.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.domain.usecase.ObserveCurrentStatsUseCase
import cz.dcervenka.choretracker.core.test.rule.TestCoroutineRule
import cz.dcervenka.choretracker.core.test.mock.sampleStatsSnapshot
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class StatsViewModelTest {

    @get:Rule
    val coroutineRule = TestCoroutineRule(startPaused = true)

    @MockK
    lateinit var observeCurrentStatsUseCase: ObserveCurrentStatsUseCase

    private val statsFlow = MutableStateFlow(sampleStatsSnapshot())

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        statsFlow.value = sampleStatsSnapshot()
        every { observeCurrentStatsUseCase() } returns statsFlow
    }

    @Test
    fun `maps current stats snapshot into ui state`() = runTest(coroutineRule.dispatcher) {
        val viewModel = StatsViewModel(observeCurrentStatsUseCase = observeCurrentStatsUseCase)

        viewModel.uiState.test {
            assertThat(awaitItem().snapshot).isNull()
            val state = awaitItem()
            assertThat(state.snapshot?.household?.id).isEqualTo("household-1")
        }
    }
}
