package cz.dcervenka.choretracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cz.dcervenka.choretracker.core.database.entity.InviteEntity
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
interface InviteDao {
    @Query("SELECT * FROM invites WHERE householdId = :householdId ORDER BY createdAt DESC")
    fun observeInvites(householdId: String): Flow<List<InviteEntity>>

    @Query("SELECT * FROM invites WHERE householdId = :householdId ORDER BY createdAt DESC")
    suspend fun getInvites(householdId: String): List<InviteEntity>

    @Query("SELECT * FROM invites WHERE code = :code LIMIT 1")
    suspend fun findByCode(code: String): InviteEntity?

    @Query(
        "SELECT * FROM invites WHERE householdId = :householdId" +
            " AND targetMemberId = :memberId AND consumedAt IS NULL LIMIT 1",
    )
    suspend fun findPendingByTargetMember(householdId: String, memberId: String): InviteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: InviteEntity)

    @Query("UPDATE invites SET consumedAt = :consumedAt WHERE id = :inviteId")
    suspend fun markConsumed(inviteId: String, consumedAt: Instant)
}
