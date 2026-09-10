package cz.dcervenka.choretracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cz.dcervenka.choretracker.core.database.entity.MemberEntity
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

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

    @Query("SELECT * FROM members WHERE householdId = :householdId AND id = :memberId LIMIT 1")
    suspend fun findById(householdId: String, memberId: String): MemberEntity?

    @Query(
        "UPDATE members SET userId = :userId, email = :email, isCurrentUser = 1, " +
            "displayName = :displayName, joinedViaInviteId = :inviteId WHERE id = :memberId",
    )
    suspend fun claimPlaceholder(
        memberId: String,
        userId: String,
        email: String?,
        displayName: String,
        inviteId: String,
    )

    @Query("UPDATE members SET isCurrentUser = 0 WHERE isCurrentUser = 1")
    suspend fun clearCurrentUser()

    // Soft removal, mirroring ChoreDao.markDeleted. The row stays so chore history keeps
    // resolving the member's name; the repository layer filters removed members out of the
    // current-member lists.
    @Query("UPDATE members SET removedAt = :removedAt WHERE id = :memberId")
    suspend fun markRemoved(memberId: String, removedAt: Instant)

    @Query("DELETE FROM members WHERE id = :id")
    suspend fun deleteById(id: String)
}
