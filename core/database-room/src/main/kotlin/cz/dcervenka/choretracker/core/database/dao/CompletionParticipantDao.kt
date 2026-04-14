package cz.dcervenka.choretracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cz.dcervenka.choretracker.core.database.entity.CompletionParticipantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CompletionParticipantDao {
    @Query(
        """
        SELECT completion_participants.* FROM completion_participants
        INNER JOIN completions ON completions.id = completion_participants.completionId
        WHERE completions.householdId = :householdId
        ORDER BY completion_participants.completionId, completion_participants.memberId
        """,
    )
    fun observeParticipants(householdId: String): Flow<List<CompletionParticipantEntity>>

    @Query(
        """
        SELECT completion_participants.* FROM completion_participants
        INNER JOIN completions ON completions.id = completion_participants.completionId
        WHERE completions.householdId = :householdId
        ORDER BY completion_participants.completionId, completion_participants.memberId
        """,
    )
    suspend fun getParticipants(householdId: String): List<CompletionParticipantEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CompletionParticipantEntity>)
}
