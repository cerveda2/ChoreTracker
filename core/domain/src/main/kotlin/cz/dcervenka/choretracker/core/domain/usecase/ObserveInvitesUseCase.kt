package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.model.household.Invite
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveInvitesUseCase @Inject constructor(
    private val householdRepository: HouseholdRepository,
) {
    operator fun invoke(householdId: String): Flow<List<Invite>> =
        householdRepository.observeInvites(householdId = householdId)
}
