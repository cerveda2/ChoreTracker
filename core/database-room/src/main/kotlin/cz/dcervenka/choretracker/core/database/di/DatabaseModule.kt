package cz.dcervenka.choretracker.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cz.dcervenka.choretracker.core.database.dao.ChoreDao
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
    ).addMigrations(MIGRATION_6_7, MIGRATION_7_8).fallbackToDestructiveMigration(dropAllTables = true).build()

    @Provides
    fun provideHouseholdDao(database: ChoreTrackerDatabase): HouseholdDao = database.householdDao()

    @Provides
    fun provideMemberDao(database: ChoreTrackerDatabase): MemberDao = database.memberDao()

    @Provides
    fun provideChoreDao(database: ChoreTrackerDatabase): ChoreDao = database.choreDao()

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
