package cz.dcervenka.choretracker.core.model.stats

import cz.dcervenka.choretracker.core.model.chore.ChoreSchedule
import kotlinx.datetime.LocalDate

data class ChoreStaleness(
    val choreId: String,
    val choreName: String,
    val lastCompletedDate: LocalDate?,
    val daysSinceLastCompletion: Int?,
    val schedule: ChoreSchedule?,
    val status: ChoreStatus,
    // Participants of the most recent completion, e.g. for "Anna did it last" - empty if never
    // completed or the completion's participants no longer resolve to a current member.
    val lastCompletedByNames: List<String> = emptyList(),
    // Days until the chore is next due, negative once overdue; null without a schedule or a last completion to
    // count from (a never-done chore has no anchor to count "due in" from).
    val dueInDays: Int? = null,
    // Days from the scheduled day the last completion counted for to the next one; null like dueInDays.
    val periodDays: Int? = null,
    // Null unless the chore rotates and at least one of its members is still in the household.
    val turn: ChoreTurn? = null,
)
