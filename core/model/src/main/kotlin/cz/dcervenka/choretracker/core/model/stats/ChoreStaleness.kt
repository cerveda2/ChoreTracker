package cz.dcervenka.choretracker.core.model.stats

import kotlinx.datetime.LocalDate

data class ChoreStaleness(
    val choreId: String,
    val choreName: String,
    val lastCompletedDate: LocalDate?,
    val daysSinceLastCompletion: Int?,
    val frequencyDays: Int?,
    val status: ChoreStatus,
)
