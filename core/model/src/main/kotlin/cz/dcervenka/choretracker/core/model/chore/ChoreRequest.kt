package cz.dcervenka.choretracker.core.model.chore

import kotlin.time.Instant

// One chore one member asks another to do. Asking for several at once writes one per chore under a
// shared batchId. Whether it's done isn't stored: any completion of the chore after createdAt counts.
data class ChoreRequest(
    val id: String,
    val householdId: String,
    val batchId: String,
    val choreId: String,
    val fromMemberId: String,
    val toMemberId: String,
    val toUserId: String,
    val createdByUserId: String,
    val note: String?,
    val createdAt: Instant,
    val state: ChoreRequestState = ChoreRequestState.OPEN,
    val resolvedAt: Instant? = null,
)

enum class ChoreRequestState {
    OPEN,
    DECLINED,
    CANCELLED,
}
