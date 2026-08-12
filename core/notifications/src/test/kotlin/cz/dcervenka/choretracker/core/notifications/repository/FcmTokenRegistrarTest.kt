package cz.dcervenka.choretracker.core.notifications.repository

import cz.dcervenka.choretracker.core.data.contract.AuthRepository
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

    private val authState = MutableStateFlow<AuthState>(AuthState.SignedOut)

    private lateinit var registrar: FcmTokenRegistrar

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { authRepository.authState } returns authState
        coEvery { tokenWriter.fetchCurrentDeviceToken() } returns "token-abc"
        coEvery { tokenWriter.writeToken(any(), any()) } returns Unit
    }

    private fun createRegistrar() {
        registrar = FcmTokenRegistrar(
            authRepository = authRepository,
            tokenWriter = tokenWriter,
            scope = CoroutineScope(SupervisorJob() + coroutineRule.dispatcher),
        )
    }

    @Test
    fun `does not register a token while signed out`() = runTest(coroutineRule.dispatcher) {
        createRegistrar()
        advanceUntilIdle()
        coVerify(exactly = 0) { tokenWriter.writeToken(any(), any()) }
    }

    @Test
    fun `does not register a token for a preview user`() = runTest(coroutineRule.dispatcher) {
        authState.value = AuthState.Authenticated(
            AppUser(id = "preview-user", email = null, displayName = "Preview User", isPreview = true),
        )
        createRegistrar()
        advanceUntilIdle()
        coVerify(exactly = 0) { tokenWriter.writeToken(any(), any()) }
    }

    @Test
    fun `registers the current device token once signed in`() = runTest(coroutineRule.dispatcher) {
        authState.value = AuthState.Authenticated(
            AppUser(id = "user-1", email = "dana@example.com", displayName = "Dana"),
        )
        createRegistrar()
        advanceUntilIdle()
        coVerify { tokenWriter.writeToken("user-1", "token-abc") }
    }

    @Test
    fun `re-registers for the newly signed-in user after sign-out then sign-in`() =
        runTest(coroutineRule.dispatcher) {
            createRegistrar()
            advanceUntilIdle()

            authState.value = AuthState.Authenticated(
                AppUser(id = "user-2", email = "sam@example.com", displayName = "Sam"),
            )
            advanceUntilIdle()

            coVerify { tokenWriter.writeToken("user-2", "token-abc") }
        }

    @Test
    fun `does not write a token when the device token fetch fails`() = runTest(coroutineRule.dispatcher) {
        coEvery { tokenWriter.fetchCurrentDeviceToken() } returns null
        authState.value = AuthState.Authenticated(
            AppUser(id = "user-1", email = "dana@example.com", displayName = "Dana"),
        )
        createRegistrar()
        advanceUntilIdle()
        coVerify(exactly = 0) { tokenWriter.writeToken(any(), any()) }
    }

    @Test
    fun `onTokenRefreshed writes the rotated token for the current user`() = runTest(coroutineRule.dispatcher) {
        authState.value = AuthState.Authenticated(
            AppUser(id = "user-1", email = "dana@example.com", displayName = "Dana"),
        )
        createRegistrar()
        advanceUntilIdle()

        registrar.onTokenRefreshed("token-rotated")
        advanceUntilIdle()

        coVerify { tokenWriter.writeToken("user-1", "token-rotated") }
    }

    @Test
    fun `onTokenRefreshed does nothing while signed out`() = runTest(coroutineRule.dispatcher) {
        createRegistrar()
        advanceUntilIdle()

        registrar.onTokenRefreshed("token-rotated")
        advanceUntilIdle()

        coVerify(exactly = 0) { tokenWriter.writeToken(any(), "token-rotated") }
    }
}
