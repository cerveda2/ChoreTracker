package cz.dcervenka.choretracker.core.sync.di

import android.content.Context
import androidx.work.WorkManager
import cz.dcervenka.choretracker.core.data.contract.SyncRepository
import cz.dcervenka.choretracker.core.sync.repository.LocalSyncRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class SyncScope

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    abstract fun bindSyncRepository(impl: LocalSyncRepository): SyncRepository

    companion object {
        @Provides
        @Singleton
        @SyncScope
        fun provideSyncScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Provides
        @Singleton
        fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
    }
}
