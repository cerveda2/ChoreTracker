package cz.dcervenka.choretracker.core.database.entity

import androidx.room.Entity

@Entity(
    tableName = "completion_participants",
    primaryKeys = ["completionId", "memberId"],
)
data class CompletionParticipantEntity(
    val completionId: String,
    val memberId: String,
)
