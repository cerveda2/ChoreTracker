package cz.dcervenka.choretracker.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "completion_participants",
    primaryKeys = ["completionId", "memberId"],
    foreignKeys = [
        ForeignKey(
            entity = CompletionEntity::class,
            parentColumns = ["id"],
            childColumns = ["completionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("completionId")],
)
data class CompletionParticipantEntity(
    val completionId: String,
    val memberId: String,
)
