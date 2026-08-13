package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.data.contract.StatsRepository
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.model.stats.ChoreStaleness
import cz.dcervenka.choretracker.core.model.stats.ChoreStatus
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class CheckStaleChoresUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val refreshHouseholdUseCase: RefreshHouseholdUseCase,
    private val householdRepository: HouseholdRepository,
    private val statsRepository: StatsRepository,
) {
    suspend operator fun invoke(): List<ChoreStaleness> {
        val user = (authRepository.authState.first() as? AuthState.Authenticated)?.user
        if (user == null || user.isPreview) return emptyList()

        // Errors are intentionally ignored here - staleness still gets computed from whatever
        // is already in Room, rather than skipping the reminder entirely on a sync failure.
        refreshHouseholdUseCase()

        val household = householdRepository.getCurrentHousehold()
        return if (household == null) {
            emptyList()
        } else {
            statsRepository.getStaleChores(household.id).filter { it.status == ChoreStatus.NEEDS_ATTENTION }
        }
    }
}
