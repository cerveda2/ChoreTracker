package cz.dcervenka.choretracker.core.database.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import cz.dcervenka.choretracker.core.database.converter.InstantConverters
import cz.dcervenka.choretracker.core.database.dao.ChoreDao
import cz.dcervenka.choretracker.core.database.dao.ChoreRequestDao
import cz.dcervenka.choretracker.core.database.dao.CompletionDao
import cz.dcervenka.choretracker.core.database.dao.CompletionParticipantDao
import cz.dcervenka.choretracker.core.database.dao.HouseholdDao
import cz.dcervenka.choretracker.core.database.dao.InviteDao
import cz.dcervenka.choretracker.core.database.dao.MemberDao
import cz.dcervenka.choretracker.core.database.dao.PendingSyncOperationDao
import cz.dcervenka.choretracker.core.database.dao.PersonalChoreDao
import cz.dcervenka.choretracker.core.database.dao.SyncStateDao
import cz.dcervenka.choretracker.core.database.entity.ChoreEntity
import cz.dcervenka.choretracker.core.database.entity.ChoreRequestEntity
import cz.dcervenka.choretracker.core.database.entity.CompletionEntity
import cz.dcervenka.choretracker.core.database.entity.CompletionParticipantEntity
import cz.dcervenka.choretracker.core.database.entity.HouseholdEntity
import cz.dcervenka.choretracker.core.database.entity.InviteEntity
import cz.dcervenka.choretracker.core.database.entity.MemberEntity
import cz.dcervenka.choretracker.core.database.entity.PendingSyncOperationEntity
import cz.dcervenka.choretracker.core.database.entity.PersonalChoreEntity
import cz.dcervenka.choretracker.core.database.entity.PersonalCompletionEntity
import cz.dcervenka.choretracker.core.database.entity.SyncStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        ChoreRequestEntity::class,
        PersonalChoreEntity::class,
        PersonalCompletionEntity::class,
    ],
    version = 16,
    exportSchema = true,
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
    abstract fun choreRequestDao(): ChoreRequestDao
    abstract fun personalChoreDao(): PersonalChoreDao

    suspend fun clearAll() = withContext(Dispatchers.IO) { clearAllTables() }

    // Leaving or losing a household: personal chores and their queued uploads belong to the user, so they stay.
    suspend fun clearHouseholdData() = withContext(Dispatchers.IO) {
        runInTransaction {
            val db = openHelper.writableDatabase
            HOUSEHOLD_TABLES.forEach { db.execSQL("DELETE FROM `$it`") }
            db.execSQL(
                "DELETE FROM pending_sync_operations WHERE entityType NOT IN ('personal_chore', 'personal_completion')",
            )
        }
    }
}

internal val HOUSEHOLD_TABLES = listOf(
    "completion_participants",
    "completions",
    "chores",
    "chore_requests",
    "invites",
    "members",
    "households",
    "sync_states",
)
