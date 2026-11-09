package cz.dcervenka.choretracker.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cz.dcervenka.choretracker.core.database.dao.ChoreDao
import cz.dcervenka.choretracker.core.database.dao.ChoreRequestDao
import cz.dcervenka.choretracker.core.database.dao.CompletionDao
import cz.dcervenka.choretracker.core.database.dao.CompletionParticipantDao
import cz.dcervenka.choretracker.core.database.dao.HouseholdDao
import cz.dcervenka.choretracker.core.database.dao.InviteDao
import cz.dcervenka.choretracker.core.database.dao.MemberDao
import cz.dcervenka.choretracker.core.database.dao.PendingSyncOperationDao
import cz.dcervenka.choretracker.core.database.dao.SyncStateDao
import cz.dcervenka.choretracker.core.database.database.ChoreTrackerDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE invites ADD COLUMN targetMemberId TEXT DEFAULT NULL")
    }
}

private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE invites ADD COLUMN consumedByMemberId TEXT DEFAULT NULL")
    }
}

private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE members ADD COLUMN joinedViaInviteId TEXT DEFAULT NULL")
    }
}

private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // INTEGER, not TEXT: InstantConverters stores Instant as epoch-millis Long.
        db.execSQL("ALTER TABLE members ADD COLUMN removedAt INTEGER DEFAULT NULL")
    }
}

private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chores ADD COLUMN createdByUserId TEXT DEFAULT NULL")
    }
}

private val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chores ADD COLUMN rotationMemberIds TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE chores ADD COLUMN rotationStartedAt INTEGER DEFAULT NULL")
    }
}

private val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `chore_requests` (`id` TEXT NOT NULL, `householdId` TEXT NOT NULL, " +
                "`batchId` TEXT NOT NULL, `choreId` TEXT NOT NULL, `fromMemberId` TEXT NOT NULL, " +
                "`toMemberId` TEXT NOT NULL, `toUserId` TEXT NOT NULL, `createdByUserId` TEXT NOT NULL, " +
                "`note` TEXT, `createdAt` INTEGER NOT NULL, `state` TEXT NOT NULL, `resolvedAt` INTEGER, " +
                "PRIMARY KEY(`id`))",
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): ChoreTrackerDatabase = Room.databaseBuilder(
        context,
        ChoreTrackerDatabase::class.java,
        "chore-tracker.db",
    ).addMigrations(
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
    )
        .build()

    @Provides
    fun provideHouseholdDao(database: ChoreTrackerDatabase): HouseholdDao = database.householdDao()

    @Provides
    fun provideMemberDao(database: ChoreTrackerDatabase): MemberDao = database.memberDao()

    @Provides
    fun provideChoreDao(database: ChoreTrackerDatabase): ChoreDao = database.choreDao()

    @Provides
    fun provideChoreRequestDao(database: ChoreTrackerDatabase): ChoreRequestDao = database.choreRequestDao()

    @Provides
    fun provideCompletionDao(database: ChoreTrackerDatabase): CompletionDao = database.completionDao()

    @Provides
    fun provideCompletionParticipantDao(database: ChoreTrackerDatabase): CompletionParticipantDao =
        database.completionParticipantDao()

    @Provides
    fun provideInviteDao(database: ChoreTrackerDatabase): InviteDao = database.inviteDao()

    @Provides
    fun providePendingSyncOperationDao(database: ChoreTrackerDatabase): PendingSyncOperationDao =
        database.pendingSyncOperationDao()

    @Provides
    fun provideSyncStateDao(database: ChoreTrackerDatabase): SyncStateDao = database.syncStateDao()
}
