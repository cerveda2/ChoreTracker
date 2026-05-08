package cz.dcervenka.choretracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cz.dcervenka.choretracker.core.database.entity.CompletionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CompletionDao {
    @Query("SELECT * FROM completions WHERE householdId = :householdId ORDER BY createdAt DESC")
    fun observeCompletions(householdId: String): Flow<List<CompletionEntity>>

    @Query("SELECT * FROM completions WHERE householdId = :householdId AND choreId = :choreId ORDER BY createdAt DESC")
    fun observeCompletionsByChore(householdId: String, choreId: String): Flow<List<CompletionEntity>>

    @Query("SELECT * FROM completions WHERE householdId = :householdId ORDER BY createdAt DESC")
    suspend fun getCompletions(householdId: String): List<CompletionEntity>

    @Query("SELECT * FROM completions WHERE id = :completionId LIMIT 1")
    suspend fun getCompletion(completionId: String): CompletionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CompletionEntity)

    @Query("DELETE FROM completions WHERE id = :completionId")
    suspend fun deleteById(completionId: String)
}
