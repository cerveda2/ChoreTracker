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

    @Query("SELECT * FROM chores WHERE householdId = :householdId ORDER BY isActive DESC, name")
    suspend fun getChores(householdId: String): List<ChoreEntity>

    @Query("SELECT * FROM chores WHERE id = :choreId LIMIT 1")
    suspend fun getChore(choreId: String): ChoreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChoreEntity)

    @Query(
        "UPDATE chores " +
            "SET isActive = 0, deletedAt = :deletedAt " +
            "WHERE id = :choreId",
    )
    suspend fun markDeleted(choreId: String, deletedAt: Instant)

    @Query(
        "UPDATE chores " +
            "SET isActive = :isActive " +
            "WHERE id = :choreId",
    )
    suspend fun updateActive(choreId: String, isActive: Boolean)
}
