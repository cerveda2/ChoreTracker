package cz.dcervenka.choretracker.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.PushTokenRepository
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.Optional

class SignOutUseCaseTest {

    @MockK
    lateinit var authRepository: AuthRepository

    @MockK
    lateinit var pushTokenRepository: PushTokenRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        coEvery { pushTokenRepository.clearCurrentUserToken() } returns Unit
        coEvery { authRepository.signOut() } returns AppResult.Success(Unit)
    }

    @Test
    fun `clears the push token before signing out`() = runTest {
        SignOutUseCase(authRepository, Optional.of(pushTokenRepository))()

        coVerifyOrder {
            pushTokenRepository.clearCurrentUserToken()
            authRepository.signOut()
        }
    }

    @Test
    fun `still signs out when the caller is cancelled while the token is being cleared`() = runTest {
        val clearing = CompletableDeferred<Unit>()
        coEvery { pushTokenRepository.clearCurrentUserToken() } coAnswers { clearing.await() }

        val caller = launch { SignOutUseCase(authRepository, Optional.of(pushTokenRepository))() }
        advanceUntilIdle()
        caller.cancel()
        clearing.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.signOut() }
    }

    @Test
    fun `signs out without a push token repository`() = runTest {
        val result = SignOutUseCase(authRepository, Optional.empty())()

        assertThat(result).isEqualTo(AppResult.Success(Unit))
        coVerify(exactly = 1) { authRepository.signOut() }
    }
}
