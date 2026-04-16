package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.model.app.StartupDestination
import cz.dcervenka.choretracker.core.model.auth.AuthState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
                combine(
                    householdRepository.observeCurrentHousehold(),
                    householdRepository.observeRestoreStatus(),
                ) { household, restoreStatus ->
                    when {
                        household != null -> StartupDestination.MAIN
                        restoreStatus.isRestoring -> null
                        else -> StartupDestination.ONBOARDING
                    }
                }.filterNotNull()
            }
        }
}
