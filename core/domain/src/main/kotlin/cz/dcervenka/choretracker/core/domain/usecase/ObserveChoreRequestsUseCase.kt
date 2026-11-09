package cz.dcervenka.choretracker.core.domain.usecase

import cz.dcervenka.choretracker.core.data.contract.ChoreRequestRepository
import cz.dcervenka.choretracker.core.data.contract.StatsRepository
import cz.dcervenka.choretracker.core.model.chore.ChoreRequest
import cz.dcervenka.choretracker.core.model.chore.ChoreRequestItem
import cz.dcervenka.choretracker.core.model.chore.ChoreRequestState
import cz.dcervenka.choretracker.core.model.chore.ChoreRequests
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

// A request is done once anyone logs its chore after it was sent, so done ones drop out here rather
// than being stored. A decline stays visible to the sender for a day, so they find out.
class ObserveChoreRequestsUseCase @Inject constructor(
    private val choreRequestRepository: ChoreRequestRepository,
    private val statsRepository: StatsRepository,
    private val clock: Clock,
) {
    operator fun invoke(householdId: String): Flow<ChoreRequests> = combine(
        choreRequestRepository.observeRequests(householdId),
        statsRepository.observeHouseholdStatsInput(householdId),
    ) { requests, input ->
        val me = input.members.find { it.isCurrentUser }
        val chores = input.chores.filter { it.deletedAt == null }.associateBy { it.id }
        val names = input.members.associate { it.id to it.displayName }
        val declinedSince = clock.now() - 1.days
        val shown = requests.filter { request ->
            val done = input.completions.any { it.choreId == request.choreId && it.createdAt > request.createdAt }
            request.choreId in chores && !done && when (request.state) {
                ChoreRequestState.OPEN -> true
                ChoreRequestState.DECLINED -> request.resolvedAt?.let { it > declinedSince } == true
                ChoreRequestState.CANCELLED, ChoreRequestState.UNKNOWN -> false
            }
        }
        fun ChoreRequest.asItem(otherMemberId: String): ChoreRequestItem {
            val chore = chores.getValue(choreId)
            return ChoreRequestItem(
                id = id,
                choreId = choreId,
                choreName = chore.name,
                category = chore.category,
                otherMemberId = otherMemberId,
                otherMemberName = names[otherMemberId].orEmpty(),
                note = note,
                createdAt = createdAt,
                declined = state == ChoreRequestState.DECLINED,
            )
        }
        if (me == null) {
            ChoreRequests()
        } else {
            ChoreRequests(
                askedOfMe = shown
                    .filter { it.toMemberId == me.id && it.state == ChoreRequestState.OPEN }
                    .map { it.asItem(it.fromMemberId) },
                waitingOn = shown.filter { it.fromMemberId == me.id }.map { it.asItem(it.toMemberId) },
            )
        }
    }
}
