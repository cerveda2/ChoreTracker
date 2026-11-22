package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.ChoreCompletionRepository
import cz.dcervenka.choretracker.core.data.contract.ChoreRepository
import cz.dcervenka.choretracker.core.domain.HouseholdStatisticsCalculator
import cz.dcervenka.choretracker.core.model.stats.ChoreStaleness
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject
import kotlin.time.Clock

// Personal chores never rotate, so there are no members to work out turns from.
class ObservePersonalStaleChoresUseCase @Inject constructor(
    private val choreRepository: ChoreRepository,
    private val choreCompletionRepository: ChoreCompletionRepository,
    private val statisticsCalculator: HouseholdStatisticsCalculator,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<List<ChoreStaleness>> =
        combine(
            choreRepository.observePersonalChores(),
            choreCompletionRepository.observePersonalCompletions(),
        ) { chores, completions ->
            statisticsCalculator.buildStaleness(
                chores = chores,
                completions = completions,
                members = emptyList(),
                timeZone = TimeZone.currentSystemDefault(),
                today = clock.todayIn(TimeZone.currentSystemDefault()),
            )
        }
}
