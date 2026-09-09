package cz.dcervenka.choretracker.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.ChoreCompletionRepository
import cz.dcervenka.choretracker.core.data.contract.ChoreRepository
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.data.contract.InviteNotificationSettingsRepository
import cz.dcervenka.choretracker.core.data.contract.ReminderSettingsRepository
import cz.dcervenka.choretracker.core.data.contract.StatsRepository
import cz.dcervenka.choretracker.core.data.contract.ThemeSettingsRepository
import cz.dcervenka.choretracker.core.data.repository.DataStoreInviteNotificationSettingsRepository
import cz.dcervenka.choretracker.core.data.repository.DataStoreReminderSettingsRepository
import cz.dcervenka.choretracker.core.data.repository.DataStoreThemeSettingsRepository
import cz.dcervenka.choretracker.core.data.repository.OfflineFirstChoreCompletionRepository
import cz.dcervenka.choretracker.core.data.repository.OfflineFirstChoreRepository
import cz.dcervenka.choretracker.core.data.repository.OfflineFirstHouseholdRepository
import cz.dcervenka.choretracker.core.data.repository.OfflineFirstStatsRepository
import cz.dcervenka.choretracker.core.data.repository.PreviewAwareAuthRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ReminderSettingsDataStore

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class InviteNotificationSettingsDataStore

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ThemeSettingsDataStore

@Module
@InstallIn(SingletonComponent::class)
abstract class DataRepositoryModule {
    @Binds
    abstract fun bindAuthRepository(impl: PreviewAwareAuthRepository): AuthRepository

    @Binds
    abstract fun bindHouseholdRepository(impl: OfflineFirstHouseholdRepository): HouseholdRepository

    @Binds
    abstract fun bindChoreRepository(impl: OfflineFirstChoreRepository): ChoreRepository

    @Binds
    abstract fun bindChoreCompletionRepository(
        impl: OfflineFirstChoreCompletionRepository,
    ): ChoreCompletionRepository

    @Binds
    abstract fun bindStatsRepository(impl: OfflineFirstStatsRepository): StatsRepository

    @Binds
    abstract fun bindReminderSettingsRepository(
        impl: DataStoreReminderSettingsRepository,
    ): ReminderSettingsRepository

    @Binds
    abstract fun bindInviteNotificationSettingsRepository(
        impl: DataStoreInviteNotificationSettingsRepository,
    ): InviteNotificationSettingsRepository

    @Binds
    abstract fun bindThemeSettingsRepository(impl: DataStoreThemeSettingsRepository): ThemeSettingsRepository

    companion object {
        @Provides
        @Singleton
        @ReminderSettingsDataStore
        fun provideReminderSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                produceFile = { context.preferencesDataStoreFile("reminder_settings") },
            )

        @Provides
        @Singleton
        @InviteNotificationSettingsDataStore
        fun provideInviteNotificationSettingsDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("invite_notification_settings") },
        )

        @Provides
        @Singleton
        @ThemeSettingsDataStore
        fun provideThemeSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                produceFile = { context.preferencesDataStoreFile("theme_settings") },
            )
    }
}
