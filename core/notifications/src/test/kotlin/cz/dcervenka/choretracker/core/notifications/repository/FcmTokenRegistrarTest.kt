package cz.dcervenka.choretracker.core.notifications.repository

import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.InviteNotificationSettingsRepository
import cz.dcervenka.choretracker.core.model.auth.AppUser
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.test.rule.TestCoroutineRule
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FcmTokenRegistrarTest {

    @get:Rule
    val coroutineRule = TestCoroutineRule(startPaused = true)

    @MockK
    lateinit var authRepository: AuthRepository

    @MockK
    lateinit var tokenWriter: FcmTokenWriter

    @MockK
    lateinit var inviteNotificationSettingsRepository: InviteNotificationSettingsRepository

    private val authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
    private val inviteNotificationsEnabled = MutableStateFlow(true)

    private lateinit var registrar: FcmTokenRegistrar

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { authRepository.authState } returns authState
        every { inviteNotificationSettingsRepository.observeEnabled(any()) } returns inviteNotificationsEnabled
        coEvery { inviteNotificationSettingsRepository.isEnabled(any()) } answers { inviteNotificationsEnabled.value }
        coEvery { tokenWriter.requestRegistration() } returns Unit
        coEvery { tokenWriter.writeToken(any(), any()) } returns Unit
        coEvery { tokenWriter.clearToken(any()) } returns Unit
    }

    private fun createRegistrar() {
        registrar = FcmTokenRegistrar(
            authRepository = authRepository,
            tokenWriter = tokenWriter,
            inviteNotificationSettingsRepository = inviteNotificationSettingsRepository,
            scope = CoroutineScope(SupervisorJob() + coroutineRule.dispatcher),
        )
    }

    @Test
    fun `does not request registration while signed out`() = runTest(coroutineRule.dispatcher) {
        createRegistrar()
        advanceUntilIdle()
        coVerify(exactly = 0) { tokenWriter.requestRegistration() }
    }

    @Test
    fun `does not request registration for a preview user`() = runTest(coroutineRule.dispatcher) {
        authState.value = AuthState.Authenticated(
            AppUser(id = "preview-user", email = null, displayName = "Preview User", isPreview = true),
        )
        createRegistrar()
        advanceUntilIdle()
        coVerify(exactly = 0) { tokenWriter.requestRegistration() }
    }

    @Test
    fun `requests registration once signed in`() = runTest(coroutineRule.dispatcher) {
        authState.value = AuthState.Authenticated(
            AppUser(id = "user-1", email = "dana@example.com", displayName = "Dana"),
        )
        createRegistrar()
        advanceUntilIdle()
        coVerify(exactly = 1) { tokenWriter.requestRegistration() }
    }

    @Test
    fun `requests registration for the newly signed-in user after sign-out then sign-in`() =
        runTest(coroutineRule.dispatcher) {
            createRegistrar()
            advanceUntilIdle()

            authState.value = AuthState.Authenticated(
                AppUser(id = "user-2", email = "sam@example.com", displayName = "Sam"),
            )
            advanceUntilIdle()

            coVerify(exactly = 1) { tokenWriter.requestRegistration() }
        }

    @Test
    fun `clears the token instead of requesting registration when notifications are disabled at sign-in`() =
        runTest(coroutineRule.dispatcher) {
            inviteNotificationsEnabled.value = false
            authState.value = AuthState.Authenticated(
                AppUser(id = "user-1", email = "dana@example.com", displayName = "Dana"),
            )
            createRegistrar()
            advanceUntilIdle()

            coVerify(exactly = 1) { tokenWriter.clearToken("user-1") }
            coVerify(exactly = 0) { tokenWriter.requestRegistration() }
        }

    @Test
    fun `clears the token when the setting is turned off while signed in`() = runTest(coroutineRule.dispatcher) {
        authState.value = AuthState.Authenticated(
            AppUser(id = "user-1", email = "dana@example.com", displayName = "Dana"),
        )
        createRegistrar()
        advanceUntilIdle()
        coVerify(exactly = 1) { tokenWriter.requestRegistration() }

        inviteNotificationsEnabled.value = false
        advanceUntilIdle()

        coVerify(exactly = 1) { tokenWriter.clearToken("user-1") }
    }

    @Test
    fun `requests registration again when the setting is turned back on`() = runTest(coroutineRule.dispatcher) {
        inviteNotificationsEnabled.value = false
        authState.value = AuthState.Authenticated(
            AppUser(id = "user-1", email = "dana@example.com", displayName = "Dana"),
        )
        createRegistrar()
        advanceUntilIdle()
        coVerify(exactly = 1) { tokenWriter.clearToken("user-1") }

        inviteNotificationsEnabled.value = true
        advanceUntilIdle()

        coVerify(exactly = 1) { tokenWriter.requestRegistration() }
    }

    @Test
    fun `onIdRegistered writes the registered installation id for the current user`() =
        runTest(coroutineRule.dispatcher) {
            authState.value = AuthState.Authenticated(
                AppUser(id = "user-1", email = "dana@example.com", displayName = "Dana"),
            )
            createRegistrar()
            advanceUntilIdle()

            registrar.onIdRegistered("id-rotated")
            advanceUntilIdle()

            coVerify { tokenWriter.writeToken("user-1", "id-rotated") }
        }

    @Test
    fun `onIdRegistered does nothing while signed out`() = runTest(coroutineRule.dispatcher) {
        createRegistrar()
        advanceUntilIdle()

        registrar.onIdRegistered("id-rotated")
        advanceUntilIdle()

        coVerify(exactly = 0) { tokenWriter.writeToken(any(), "id-rotated") }
    }

    @Test
    fun `onIdRegistered does not write while notifications are disabled`() = runTest(coroutineRule.dispatcher) {
        inviteNotificationsEnabled.value = false
        authState.value = AuthState.Authenticated(
            AppUser(id = "user-1", email = "dana@example.com", displayName = "Dana"),
        )
        createRegistrar()
        advanceUntilIdle()

        registrar.onIdRegistered("id-rotated")
        advanceUntilIdle()

        coVerify(exactly = 0) { tokenWriter.writeToken(any(), "id-rotated") }
    }
}
