package cz.dcervenka.choretracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cz.dcervenka.choretracker.core.database.entity.ChoreEntity
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
interface ChoreDao {
    @Query("SELECT * FROM chores WHERE householdId = :householdId ORDER BY isActive DESC, name")
    fun observeChores(householdId: String): Flow<List<ChoreEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChoreEntity)

    @Query(
        "UPDATE chores " +
            "SET isActive = :isActive, deletedAt = CASE WHEN :isActive THEN NULL ELSE :deletedAt END " +
            "WHERE id = :choreId",
    )
    suspend fun updateActive(choreId: String, isActive: Boolean, deletedAt: Instant?)
}
