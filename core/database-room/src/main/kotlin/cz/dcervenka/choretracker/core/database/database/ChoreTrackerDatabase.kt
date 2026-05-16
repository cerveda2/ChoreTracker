package cz.dcervenka.choretracker.core.database.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import cz.dcervenka.choretracker.core.database.converter.InstantConverters
import cz.dcervenka.choretracker.core.database.dao.ChoreDao
import cz.dcervenka.choretracker.core.database.dao.CompletionDao
import cz.dcervenka.choretracker.core.database.dao.CompletionParticipantDao
import cz.dcervenka.choretracker.core.database.dao.HouseholdDao
import cz.dcervenka.choretracker.core.database.dao.InviteDao
import cz.dcervenka.choretracker.core.database.dao.MemberDao
import cz.dcervenka.choretracker.core.database.dao.PendingSyncOperationDao
import cz.dcervenka.choretracker.core.database.dao.SyncStateDao
import cz.dcervenka.choretracker.core.database.entity.ChoreEntity
import cz.dcervenka.choretracker.core.database.entity.CompletionEntity
import cz.dcervenka.choretracker.core.database.entity.CompletionParticipantEntity
import cz.dcervenka.choretracker.core.database.entity.HouseholdEntity
import cz.dcervenka.choretracker.core.database.entity.InviteEntity
import cz.dcervenka.choretracker.core.database.entity.MemberEntity
import cz.dcervenka.choretracker.core.database.entity.PendingSyncOperationEntity
import cz.dcervenka.choretracker.core.database.entity.SyncStateEntity

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
    version = 7,
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
