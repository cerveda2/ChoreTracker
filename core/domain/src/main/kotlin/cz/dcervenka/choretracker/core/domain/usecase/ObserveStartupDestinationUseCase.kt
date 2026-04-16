package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.model.app.StartupDestination
import cz.dcervenka.choretracker.core.model.auth.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveStartupDestinationUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository,
) {
    operator fun invoke(): Flow<StartupDestination> = authRepository.authState
        .filter { it !is AuthState.Initializing }
        .flatMapLatest { authState ->
            if (authState !is AuthState.Authenticated) {
                flowOf(StartupDestination.AUTH)
            } else {
                householdRepository.observeCurrentHousehold().map { household ->
                    if (household == null) StartupDestination.ONBOARDING else StartupDestination.MAIN
                }
            }
        }
}
