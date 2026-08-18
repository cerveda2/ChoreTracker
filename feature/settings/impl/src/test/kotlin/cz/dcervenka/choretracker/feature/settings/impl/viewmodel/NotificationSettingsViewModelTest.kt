package cz.dcervenka.choretracker.feature.settings.impl.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.domain.usecase.ObserveInviteNotificationsEnabledUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveReminderSettingsUseCase
import cz.dcervenka.choretracker.core.domain.usecase.SetInviteNotificationsEnabledUseCase
import cz.dcervenka.choretracker.core.domain.usecase.SetReminderEnabledUseCase
import cz.dcervenka.choretracker.core.domain.usecase.SetReminderTimeUseCase
import cz.dcervenka.choretracker.core.model.settings.ReminderSettings
import cz.dcervenka.choretracker.core.test.rule.TestCoroutineRule
import cz.dcervenka.choretracker.feature.settings.impl.contract.NotificationSettingsUiIntent
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NotificationSettingsViewModelTest {

    @get:Rule
    val coroutineRule = TestCoroutineRule(startPaused = true)

    @MockK
    lateinit var observeReminderSettingsUseCase: ObserveReminderSettingsUseCase

    @MockK
    lateinit var observeInviteNotificationsEnabledUseCase: ObserveInviteNotificationsEnabledUseCase

    @MockK
    lateinit var setReminderEnabledUseCase: SetReminderEnabledUseCase

    @MockK
    lateinit var setReminderTimeUseCase: SetReminderTimeUseCase

    @MockK
    lateinit var setInviteNotificationsEnabledUseCase: SetInviteNotificationsEnabledUseCase

    private val reminderSettingsFlow = MutableStateFlow(ReminderSettings())
    private val inviteNotificationsEnabledFlow = MutableStateFlow(true)

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { observeReminderSettingsUseCase() } returns reminderSettingsFlow
        every { observeInviteNotificationsEnabledUseCase() } returns inviteNotificationsEnabledFlow
        coEvery { setReminderEnabledUseCase(any()) } returns Unit
        coEvery { setReminderTimeUseCase(any(), any()) } returns Unit
        coEvery { setInviteNotificationsEnabledUseCase(any()) } returns Unit
    }

    private fun createViewModel() = NotificationSettingsViewModel(
        observeReminderSettingsUseCase,
        observeInviteNotificationsEnabledUseCase,
        setReminderEnabledUseCase,
        setReminderTimeUseCase,
        setInviteNotificationsEnabledUseCase,
    )

    @Test
    fun `maps observed settings into ui state`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()
        reminderSettingsFlow.value = ReminderSettings(enabled = false, hour = 20, minute = 15)
        inviteNotificationsEnabledFlow.value = false

        viewModel.uiState.test {
            assertThat(awaitItem().isLoading).isTrue()

            val loaded = awaitItem()
            assertThat(loaded.isLoading).isFalse()
            assertThat(loaded.remindersEnabled).isFalse()
            assertThat(loaded.hour).isEqualTo(20)
            assertThat(loaded.minute).isEqualTo(15)
            assertThat(loaded.inviteNotificationsEnabled).isFalse()
        }
    }

    @Test
    fun `SetRemindersEnabled intent delegates to use case`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.dispatch(NotificationSettingsUiIntent.SetRemindersEnabled(false))
        advanceUntilIdle()

        coVerify(exactly = 1) { setReminderEnabledUseCase(false) }
    }

    @Test
    fun `SetTime intent delegates to use case`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.dispatch(NotificationSettingsUiIntent.SetTime(hour = 7, minute = 45))
        advanceUntilIdle()

        coVerify(exactly = 1) { setReminderTimeUseCase(7, 45) }
    }

    @Test
    fun `SetInviteNotificationsEnabled intent delegates to use case`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.dispatch(NotificationSettingsUiIntent.SetInviteNotificationsEnabled(false))
        advanceUntilIdle()

        coVerify(exactly = 1) { setInviteNotificationsEnabledUseCase(false) }
    }
}
