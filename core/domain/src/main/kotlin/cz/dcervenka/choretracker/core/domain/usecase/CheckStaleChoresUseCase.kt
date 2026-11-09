package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.data.contract.StatsRepository
import cz.dcervenka.choretracker.core.domain.HouseholdStatisticsCalculator
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.model.stats.ChoreStaleness
import cz.dcervenka.choretracker.core.model.stats.ChoreStatus
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject
import kotlin.time.Clock

class CheckStaleChoresUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val refreshHouseholdUseCase: RefreshHouseholdUseCase,
    private val householdRepository: HouseholdRepository,
    private val statsRepository: StatsRepository,
    private val statisticsCalculator: HouseholdStatisticsCalculator,
    private val observeChoreRequestsUseCase: ObserveChoreRequestsUseCase,
    private val clock: Clock,
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
            val input = statsRepository.getHouseholdStatsInput(household.id)
            // A chore someone asked you to do is included even before it's overdue, and one you've asked
            // someone else to do is left to them.
            val requests = observeChoreRequestsUseCase(household.id).first()
            val askedOfMe = requests.askedOfMe.map { it.choreId }.toSet()
            val askedOfOthers = requests.waitingOn.filterNot { it.declined }.map { it.choreId }.toSet()
            statisticsCalculator.buildStaleness(
                chores = input.chores,
                completions = input.completions,
                members = input.members,
                timeZone = TimeZone.currentSystemDefault(),
                today = clock.todayIn(TimeZone.currentSystemDefault()),
            ).filter { stale ->
                stale.choreId in askedOfMe ||
                    (
                        stale.status == ChoreStatus.NEEDS_ATTENTION &&
                            stale.turn?.isCurrentUser != false &&
                            stale.choreId !in askedOfOthers
                        )
            }
        }
    }
}
