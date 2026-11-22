package cz.dcervenka.choretracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cz.dcervenka.choretracker.core.database.entity.PersonalChoreEntity
import cz.dcervenka.choretracker.core.database.entity.PersonalCompletionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonalChoreDao {
    @Query(
        "SELECT * FROM personal_chores WHERE ownerUserId = :ownerUserId AND deletedAt IS NULL " +
            "ORDER BY isActive DESC, name",
    )
    fun observeChores(ownerUserId: String): Flow<List<PersonalChoreEntity>>

    @Query(
        "SELECT * FROM personal_chores WHERE ownerUserId = :ownerUserId AND deletedAt IS NULL " +
            "ORDER BY isActive DESC, name",
    )
    suspend fun getChores(ownerUserId: String): List<PersonalChoreEntity>

    @Query("SELECT * FROM personal_chores WHERE id = :choreId LIMIT 1")
    suspend fun getChore(choreId: String): PersonalChoreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChore(entity: PersonalChoreEntity)

    @Query("SELECT * FROM personal_completions WHERE ownerUserId = :ownerUserId ORDER BY createdAt DESC")
    fun observeCompletions(ownerUserId: String): Flow<List<PersonalCompletionEntity>>

    @Query("SELECT * FROM personal_completions WHERE ownerUserId = :ownerUserId ORDER BY createdAt DESC")
    suspend fun getCompletions(ownerUserId: String): List<PersonalCompletionEntity>

    @Query("SELECT * FROM personal_completions WHERE id = :completionId LIMIT 1")
    suspend fun getCompletion(completionId: String): PersonalCompletionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletion(entity: PersonalCompletionEntity)

    @Query("DELETE FROM personal_completions WHERE id = :completionId")
    suspend fun deleteCompletion(completionId: String)
}
