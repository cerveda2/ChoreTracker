package cz.dcervenka.choretracker.feature.settings.impl.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.domain.usecase.ObserveCurrentHouseholdUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveInviteNotificationsEnabledUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveMembersUseCase
import cz.dcervenka.choretracker.core.domain.usecase.ObserveReminderSettingsUseCase
import cz.dcervenka.choretracker.core.domain.usecase.SetInviteNotificationsEnabledUseCase
import cz.dcervenka.choretracker.core.domain.usecase.SetReminderEnabledUseCase
import cz.dcervenka.choretracker.core.domain.usecase.SetReminderTimeUseCase
import cz.dcervenka.choretracker.core.model.household.Household
import cz.dcervenka.choretracker.core.model.household.HouseholdMember
import cz.dcervenka.choretracker.core.model.household.HouseholdRole
import cz.dcervenka.choretracker.core.model.settings.ReminderSettings
import cz.dcervenka.choretracker.core.test.mock.sampleHousehold
import cz.dcervenka.choretracker.core.test.mock.sampleMembers
import cz.dcervenka.choretracker.core.test.rule.TestCoroutineRule
import cz.dcervenka.choretracker.feature.settings.impl.contract.NotificationSettingsUiIntent
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
    lateinit var observeCurrentHouseholdUseCase: ObserveCurrentHouseholdUseCase

    @MockK
    lateinit var observeMembersUseCase: ObserveMembersUseCase

    @MockK
    lateinit var setReminderEnabledUseCase: SetReminderEnabledUseCase

    @MockK
    lateinit var setReminderTimeUseCase: SetReminderTimeUseCase

    @MockK
    lateinit var setInviteNotificationsEnabledUseCase: SetInviteNotificationsEnabledUseCase

    private val reminderSettingsFlow = MutableStateFlow(ReminderSettings())
    private val inviteNotificationsEnabledFlow = MutableStateFlow(true)
    private val householdFlow = MutableStateFlow<Household?>(null)
    private val membersFlow = MutableStateFlow(emptyList<HouseholdMember>())

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { observeReminderSettingsUseCase() } returns reminderSettingsFlow
        every { observeInviteNotificationsEnabledUseCase() } returns inviteNotificationsEnabledFlow
        every { observeCurrentHouseholdUseCase() } returns householdFlow
        every { observeMembersUseCase(any()) } returns membersFlow
        coEvery { setReminderEnabledUseCase(any()) } returns Unit
        coEvery { setReminderTimeUseCase(any(), any()) } returns Unit
        coEvery { setInviteNotificationsEnabledUseCase(any()) } returns Unit
    }

    private fun createViewModel() = NotificationSettingsViewModel(
        observeReminderSettingsUseCase,
        observeInviteNotificationsEnabledUseCase,
        observeCurrentHouseholdUseCase,
        observeMembersUseCase,
        setReminderEnabledUseCase,
        setReminderTimeUseCase,
        setInviteNotificationsEnabledUseCase,
    )

    @Test
    fun `only the owner and admins can turn invite notifications on or off`() = runTest(coroutineRule.dispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        householdFlow.value = sampleHousehold()
        membersFlow.value = sampleMembers()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.canInvite).isTrue()

        membersFlow.value = sampleMembers().map { it.copy(isCurrentUser = it.id == "member-2") }
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.canInvite).isFalse()

        membersFlow.value = sampleMembers().map {
            it.copy(isCurrentUser = it.id == "member-2", role = HouseholdRole.ADMIN)
        }
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.canInvite).isTrue()
    }

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
