package cz.dcervenka.choretracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cz.dcervenka.choretracker.core.database.entity.PendingSyncOperationEntity

@Dao
interface PendingSyncOperationDao {
    @Query("SELECT * FROM pending_sync_operations ORDER BY createdAt")
    suspend fun getAll(): List<PendingSyncOperationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PendingSyncOperationEntity)

    @Query("DELETE FROM pending_sync_operations WHERE id = :operationId")
    suspend fun delete(operationId: String)

    @Query("SELECT COUNT(*) FROM pending_sync_operations")
    suspend fun pendingCount(): Int
}
