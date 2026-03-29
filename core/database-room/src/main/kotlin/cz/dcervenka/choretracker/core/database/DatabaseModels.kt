package cz.dcervenka.choretracker.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Entity(tableName = "households")
data class HouseholdEntity(
    @PrimaryKey val id: String,
    val name: String,
    val ownerUserId: String,
    val inviteCode: String,
    val createdAt: Instant,
)

@Entity(tableName = "members")
data class MemberEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val userId: String?,
    val displayName: String,
    val role: String,
    val isCurrentUser: Boolean,
)

@Entity(tableName = "chores")
data class ChoreEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val name: String,
    val isActive: Boolean,
    val createdAt: Instant,
    val deletedAt: Instant?,
)

@Entity(tableName = "completions")
data class CompletionEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val choreId: String,
    val createdAt: Instant,
    val createdByUserId: String,
    val note: String?,
)

@Entity(
    tableName = "completion_participants",
    primaryKeys = ["completionId", "memberId"],
)
data class CompletionParticipantEntity(
    val completionId: String,
    val memberId: String,
)

@Entity(tableName = "invites")
data class InviteEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val code: String,
    val createdAt: Instant,
    val consumedAt: Instant?,
)

@Entity(tableName = "pending_sync_operations")
data class PendingSyncOperationEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val operationType: String,
    val payload: String,
    val createdAt: Instant,
)

@Entity(tableName = "sync_states")
data class SyncStateEntity(
    @PrimaryKey val householdId: String,
    val lastSyncedAt: Instant?,
    val pendingOperations: Int,
)

class InstantConverters {
    @TypeConverter
    fun instantToLong(value: Instant?): Long? = value?.toEpochMilliseconds()

    @TypeConverter
    fun longToInstant(value: Long?): Instant? = value?.let(Instant::fromEpochMilliseconds)
}

@Dao
interface HouseholdDao {
    @Query("SELECT * FROM households ORDER BY createdAt DESC LIMIT 1")
    fun observeCurrentHousehold(): Flow<HouseholdEntity?>

    @Query("SELECT * FROM households WHERE id = :householdId LIMIT 1")
    fun observeHousehold(householdId: String): Flow<HouseholdEntity?>

    @Query("SELECT * FROM households WHERE id = :householdId LIMIT 1")
    suspend fun getHousehold(householdId: String): HouseholdEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HouseholdEntity)

    @Query("UPDATE households SET inviteCode = :inviteCode WHERE id = :householdId")
    suspend fun updateInviteCode(householdId: String, inviteCode: String)
}

@Dao
interface MemberDao {
    @Query("SELECT * FROM members WHERE householdId = :householdId ORDER BY CASE WHEN isCurrentUser THEN 0 ELSE 1 END, displayName")
    fun observeMembers(householdId: String): Flow<List<MemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MemberEntity)

    @Query("SELECT * FROM members WHERE householdId = :householdId AND userId = :userId LIMIT 1")
    suspend fun findByUserId(householdId: String, userId: String): MemberEntity?
}

@Dao
interface ChoreDao {
    @Query("SELECT * FROM chores WHERE householdId = :householdId ORDER BY isActive DESC, name")
    fun observeChores(householdId: String): Flow<List<ChoreEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChoreEntity)

    @Query("UPDATE chores SET isActive = :isActive, deletedAt = CASE WHEN :isActive THEN NULL ELSE :deletedAt END WHERE id = :choreId")
    suspend fun updateActive(choreId: String, isActive: Boolean, deletedAt: Instant?)
}

@Dao
interface CompletionDao {
    @Query("SELECT * FROM completions WHERE householdId = :householdId ORDER BY createdAt DESC")
    fun observeCompletions(householdId: String): Flow<List<CompletionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CompletionEntity)
}

@Dao
interface CompletionParticipantDao {
    @Query(
        """
        SELECT completion_participants.* FROM completion_participants
        INNER JOIN completions ON completions.id = completion_participants.completionId
        WHERE completions.householdId = :householdId
        """,
    )
    fun observeParticipants(householdId: String): Flow<List<CompletionParticipantEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CompletionParticipantEntity>)
}

@Dao
interface InviteDao {
    @Query("SELECT * FROM invites WHERE householdId = :householdId ORDER BY createdAt DESC")
    fun observeInvites(householdId: String): Flow<List<InviteEntity>>

    @Query("SELECT * FROM invites WHERE code = :code LIMIT 1")
    suspend fun findByCode(code: String): InviteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: InviteEntity)

    @Query("UPDATE invites SET consumedAt = :consumedAt WHERE id = :inviteId")
    suspend fun markConsumed(inviteId: String, consumedAt: Instant)
}

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

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_states WHERE householdId = :householdId LIMIT 1")
    fun observeSyncState(householdId: String): Flow<SyncStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncStateEntity)
}

@Database(
    entities = [
        HouseholdEntity::class,
        MemberEntity::class,
        ChoreEntity::class,
        CompletionEntity::class,
        CompletionParticipantEntity::class,
        InviteEntity::class,
        PendingSyncOperationEntity::class,
        SyncStateEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(InstantConverters::class)
abstract class ChoreTrackerDatabase : RoomDatabase() {
    abstract fun householdDao(): HouseholdDao
    abstract fun memberDao(): MemberDao
    abstract fun choreDao(): ChoreDao
    abstract fun completionDao(): CompletionDao
    abstract fun completionParticipantDao(): CompletionParticipantDao
    abstract fun inviteDao(): InviteDao
    abstract fun pendingSyncOperationDao(): PendingSyncOperationDao
    abstract fun syncStateDao(): SyncStateDao
}
