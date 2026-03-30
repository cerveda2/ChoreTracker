package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.model.app.StartupDestination
import cz.dcervenka.choretracker.core.model.auth.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class ObserveStartupDestinationUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository,
) {
    operator fun invoke(): Flow<StartupDestination> = combine(
        authRepository.authState,
        householdRepository.observeCurrentHousehold(),
    ) { authState, household ->
        when {
            authState !is AuthState.Authenticated -> StartupDestination.AUTH
            household == null -> StartupDestination.ONBOARDING
            else -> StartupDestination.MAIN
        }
    }
}
