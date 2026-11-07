package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.PushTokenRepository
import javax.inject.Inject

class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val pushTokenRepository: PushTokenRepository,
) {
    // The token lives on users/{uid}, which rules only let that signed-in user write, so it has to
    // go first - otherwise this device keeps getting the signed-out account's pushes.
    suspend operator fun invoke(): EmptyResult {
        pushTokenRepository.clearCurrentUserToken()
        return authRepository.signOut()
    }
}
