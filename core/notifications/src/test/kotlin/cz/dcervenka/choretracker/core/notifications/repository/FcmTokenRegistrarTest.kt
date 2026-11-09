package cz.dcervenka.choretracker.core.notifications.repository

import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.InviteNotificationSettingsRepository
import cz.dcervenka.choretracker.core.data.contract.RequestNotificationSettingsRepository
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
import kotlinx.coroutines.awaitCancellation
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

    @MockK
    lateinit var requestNotificationSettingsRepository: RequestNotificationSettingsRepository

    private val authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
    private val inviteNotificationsEnabled = MutableStateFlow(true)
    private val requestNotificationsEnabled = MutableStateFlow(true)

    private lateinit var registrar: FcmTokenRegistrar

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { authRepository.authState } returns authState
        every { inviteNotificationSettingsRepository.observeEnabled(any()) } returns inviteNotificationsEnabled
        coEvery { inviteNotificationSettingsRepository.isEnabled(any()) } answers { inviteNotificationsEnabled.value }
        every { requestNotificationSettingsRepository.observeEnabled(any()) } returns requestNotificationsEnabled
        coEvery { requestNotificationSettingsRepository.isEnabled(any()) } answers { requestNotificationsEnabled.value }
        coEvery { tokenWriter.requestRegistration() } returns Unit
        coEvery { tokenWriter.writeToken(any(), any(), any()) } returns Unit
        coEvery { tokenWriter.writePushTypes(any(), any()) } returns Unit
        coEvery { tokenWriter.clearToken(any()) } returns Unit
    }

    private fun createRegistrar() {
        registrar = FcmTokenRegistrar(
            authRepository = authRepository,
            tokenWriter = tokenWriter,
            inviteNotificationSettingsRepository = inviteNotificationSettingsRepository,
            requestNotificationSettingsRepository = requestNotificationSettingsRepository,
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
    fun `clearCurrentUserToken clears the signed-in user's token`() = runTest(coroutineRule.dispatcher) {
        authState.value = AuthState.Authenticated(
            AppUser(id = "user-1", email = "dana@example.com", displayName = "Dana"),
        )
        createRegistrar()
        advanceUntilIdle()

        registrar.clearCurrentUserToken()

        coVerify(exactly = 1) { tokenWriter.clearToken("user-1") }
    }

    @Test
    fun `clearCurrentUserToken does nothing for a preview user`() = runTest(coroutineRule.dispatcher) {
        authState.value = AuthState.Authenticated(
            AppUser(id = "preview-user", email = null, displayName = "Preview User", isPreview = true),
        )
        createRegistrar()
        advanceUntilIdle()

        registrar.clearCurrentUserToken()

        coVerify(exactly = 0) { tokenWriter.clearToken(any()) }
    }

    @Test
    fun `clearCurrentUserToken gives up when the write doesn't finish in time`() = runTest(coroutineRule.dispatcher) {
        authState.value = AuthState.Authenticated(
            AppUser(id = "user-1", email = "dana@example.com", displayName = "Dana"),
        )
        coEvery { tokenWriter.clearToken(any()) } coAnswers { awaitCancellation() }
        createRegistrar()
        advanceUntilIdle()

        registrar.clearCurrentUserToken()

        coVerify(exactly = 1) { tokenWriter.clearToken("user-1") }
    }

    @Test
    fun `does not try to clear the token once already signed out`() = runTest(coroutineRule.dispatcher) {
        authState.value = AuthState.Authenticated(
            AppUser(id = "user-1", email = "dana@example.com", displayName = "Dana"),
        )
        createRegistrar()
        advanceUntilIdle()

        authState.value = AuthState.SignedOut
        advanceUntilIdle()

        coVerify(exactly = 0) { tokenWriter.clearToken(any()) }
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
    fun `keeps the token and drops invite from pushTypes when invite pushes are off at sign-in`() =
        runTest(coroutineRule.dispatcher) {
            inviteNotificationsEnabled.value = false
            authState.value = AuthState.Authenticated(
                AppUser(id = "user-1", email = "dana@example.com", displayName = "Dana"),
            )
            createRegistrar()
            advanceUntilIdle()

            coVerify(exactly = 1) { tokenWriter.writePushTypes("user-1", listOf("request")) }
            coVerify(exactly = 1) { tokenWriter.requestRegistration() }
            coVerify(exactly = 0) { tokenWriter.clearToken(any()) }
        }

    @Test
    fun `updates pushTypes, not the token, when the setting is switched`() = runTest(coroutineRule.dispatcher) {
        authState.value = AuthState.Authenticated(
            AppUser(id = "user-1", email = "dana@example.com", displayName = "Dana"),
        )
        createRegistrar()
        advanceUntilIdle()
        coVerify(exactly = 1) { tokenWriter.writePushTypes("user-1", listOf("invite", "request")) }

        inviteNotificationsEnabled.value = false
        advanceUntilIdle()
        coVerify(exactly = 1) { tokenWriter.writePushTypes("user-1", listOf("request")) }

        inviteNotificationsEnabled.value = true
        advanceUntilIdle()
        coVerify(exactly = 2) { tokenWriter.writePushTypes("user-1", listOf("invite", "request")) }
        coVerify(exactly = 0) { tokenWriter.clearToken(any()) }
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

            coVerify { tokenWriter.writeToken("user-1", "id-rotated", listOf("invite", "request")) }
        }

    @Test
    fun `onIdRegistered does nothing while signed out`() = runTest(coroutineRule.dispatcher) {
        createRegistrar()
        advanceUntilIdle()

        registrar.onIdRegistered("id-rotated")
        advanceUntilIdle()

        coVerify(exactly = 0) { tokenWriter.writeToken(any(), "id-rotated", any()) }
    }

    @Test
    fun `onIdRegistered does not write the token back while signing out`() = runTest(coroutineRule.dispatcher) {
        authState.value = AuthState.Authenticated(
            AppUser(id = "user-1", email = "dana@example.com", displayName = "Dana"),
        )
        createRegistrar()
        advanceUntilIdle()

        registrar.clearCurrentUserToken()
        registrar.onIdRegistered("id-late")
        advanceUntilIdle()

        coVerify(exactly = 0) { tokenWriter.writeToken(any(), "id-late", any()) }
    }

    @Test
    fun `onIdRegistered writes again after signing back in`() = runTest(coroutineRule.dispatcher) {
        val user = AppUser(id = "user-1", email = "dana@example.com", displayName = "Dana")
        authState.value = AuthState.Authenticated(user)
        createRegistrar()
        advanceUntilIdle()
        registrar.clearCurrentUserToken()
        authState.value = AuthState.SignedOut
        advanceUntilIdle()

        authState.value = AuthState.Authenticated(user)
        advanceUntilIdle()
        registrar.onIdRegistered("id-new")
        advanceUntilIdle()

        coVerify { tokenWriter.writeToken("user-1", "id-new", listOf("invite", "request")) }
    }

    @Test
    fun `onIdRegistered still writes the token, without invite, while invite pushes are off`() =
        runTest(coroutineRule.dispatcher) {
            inviteNotificationsEnabled.value = false
            authState.value = AuthState.Authenticated(
                AppUser(id = "user-1", email = "dana@example.com", displayName = "Dana"),
            )
            createRegistrar()
            advanceUntilIdle()

            registrar.onIdRegistered("id-rotated")
            advanceUntilIdle()

            coVerify { tokenWriter.writeToken("user-1", "id-rotated", listOf("request")) }
        }

    @Test
    fun `the request toggle only takes request out of pushTypes`() = runTest(coroutineRule.dispatcher) {
        authState.value = AuthState.Authenticated(
            AppUser(id = "user-1", email = "dana@example.com", displayName = "Dana"),
        )
        createRegistrar()
        advanceUntilIdle()

        requestNotificationsEnabled.value = false
        advanceUntilIdle()
        registrar.onIdRegistered("id-rotated")
        advanceUntilIdle()

        coVerify(exactly = 1) { tokenWriter.writePushTypes("user-1", listOf("invite")) }
        coVerify { tokenWriter.writeToken("user-1", "id-rotated", listOf("invite")) }
        coVerify(exactly = 0) { tokenWriter.clearToken(any()) }
    }
}
