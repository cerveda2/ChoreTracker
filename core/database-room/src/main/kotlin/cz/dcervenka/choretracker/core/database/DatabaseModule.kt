package cz.dcervenka.choretracker.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
    ).fallbackToDestructiveMigration(dropAllTables = true).build()

    @Provides
    fun provideHouseholdDao(database: ChoreTrackerDatabase): HouseholdDao = database.householdDao()

    @Provides
    fun provideMemberDao(database: ChoreTrackerDatabase): MemberDao = database.memberDao()

    @Provides
    fun provideChoreDao(database: ChoreTrackerDatabase): ChoreDao = database.choreDao()

    @Provides
    fun provideCompletionDao(database: ChoreTrackerDatabase): CompletionDao = database.completionDao()

    @Provides
    fun provideCompletionParticipantDao(
        database: ChoreTrackerDatabase,
    ): CompletionParticipantDao = database.completionParticipantDao()

    @Provides
    fun provideInviteDao(database: ChoreTrackerDatabase): InviteDao = database.inviteDao()

    @Provides
    fun providePendingSyncOperationDao(
        database: ChoreTrackerDatabase,
    ): PendingSyncOperationDao = database.pendingSyncOperationDao()

    @Provides
    fun provideSyncStateDao(database: ChoreTrackerDatabase): SyncStateDao = database.syncStateDao()
}
