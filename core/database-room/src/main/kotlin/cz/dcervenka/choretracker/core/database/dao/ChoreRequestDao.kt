package cz.dcervenka.choretracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import cz.dcervenka.choretracker.core.database.entity.ChoreRequestEntity
import cz.dcervenka.choretracker.core.database.entity.PendingSyncOperationEntity
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
interface ChoreRequestDao {
    @Query("SELECT * FROM chore_requests WHERE householdId = :householdId ORDER BY createdAt DESC")
    fun observeRequests(householdId: String): Flow<List<ChoreRequestEntity>>

    @Query("SELECT * FROM chore_requests WHERE householdId = :householdId")
    suspend fun getRequests(householdId: String): List<ChoreRequestEntity>

    @Query("SELECT * FROM chore_requests WHERE id = :id LIMIT 1")
    suspend fun getRequest(id: String): ChoreRequestEntity?

    @Query("SELECT * FROM chore_requests WHERE batchId = :batchId")
    suspend fun getBatch(batchId: String): List<ChoreRequestEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ChoreRequestEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperation(operation: PendingSyncOperationEntity)

    // One transaction, so a sync running meanwhile sees the whole batch and its operation or neither.
    @Transaction
    suspend fun insertBatch(requests: List<ChoreRequestEntity>, operation: PendingSyncOperationEntity) {
        upsertAll(requests)
        insertOperation(operation)
    }

    @Query("UPDATE chore_requests SET state = :state, resolvedAt = :resolvedAt WHERE id = :id")
    suspend fun updateState(id: String, state: String, resolvedAt: Instant)

    @Query("DELETE FROM chore_requests WHERE id = :id")
    suspend fun deleteById(id: String)
}
