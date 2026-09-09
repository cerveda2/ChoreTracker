package cz.dcervenka.choretracker.core.model.stats

import kotlinx.datetime.LocalDate

data class ChoreStaleness(
    val choreId: String,
    val choreName: String,
    val lastCompletedDate: LocalDate?,
    val daysSinceLastCompletion: Int?,
    val frequencyDays: Int?,
    val status: ChoreStatus,
    // Participants of the most recent completion, e.g. for "Anna did it last" - empty if never
    // completed or the completion's participants no longer resolve to a current member.
    val lastCompletedByNames: List<String> = emptyList(),
    // frequencyDays - daysSinceLastCompletion; null without a frequency or a last completion to
    // count from (a never-done chore has no anchor to count "due in" from).
    val dueInDays: Int? = null,
)
