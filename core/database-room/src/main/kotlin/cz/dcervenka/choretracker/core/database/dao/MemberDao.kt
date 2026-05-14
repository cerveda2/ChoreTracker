package cz.dcervenka.choretracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cz.dcervenka.choretracker.core.database.entity.MemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {
    @Query(
        "SELECT * FROM members " +
            "WHERE householdId = :householdId " +
            "ORDER BY CASE WHEN isCurrentUser THEN 0 ELSE 1 END, displayName",
    )
    fun observeMembers(householdId: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE householdId = :householdId ORDER BY displayName")
    suspend fun getMembers(householdId: String): List<MemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MemberEntity)

    @Query("SELECT * FROM members WHERE householdId = :householdId AND userId = :userId LIMIT 1")
    suspend fun findByUserId(householdId: String, userId: String): MemberEntity?

    @Query("SELECT * FROM members WHERE householdId = :householdId AND isCurrentUser = 1 LIMIT 1")
    suspend fun findCurrentUser(householdId: String): MemberEntity?

    @Query("DELETE FROM members WHERE id = :id")
    suspend fun deleteById(id: String)
}
