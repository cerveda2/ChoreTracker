package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.common.EmptyResult
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.PushTokenRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.Optional
import javax.inject.Inject

class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    // Bound only by core/notifications; without that module there's no token to clear.
    private val pushTokenRepository: Optional<PushTokenRepository>,
) {
    // The token lives on users/{uid}, which rules only let that signed-in user write, so it has to
    // go first - otherwise this device keeps getting the signed-out account's pushes.
    // NonCancellable: the clear can take a few seconds, and leaving the screen meanwhile mustn't
    // skip the sign-out itself.
    suspend operator fun invoke(): EmptyResult = withContext(NonCancellable) {
        pushTokenRepository.orElse(null)?.clearCurrentUserToken()
        authRepository.signOut()
    }
}
