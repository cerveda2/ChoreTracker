package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.PushTokenRepository
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

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
        SignOutUseCase(authRepository, pushTokenRepository)()

        coVerifyOrder {
            pushTokenRepository.clearCurrentUserToken()
            authRepository.signOut()
        }
    }
}
