package cz.dcervenka.choretracker.core.notifications.di

import cz.dcervenka.choretracker.core.notifications.repository.FcmTokenWriter
import cz.dcervenka.choretracker.core.notifications.repository.FirebaseFcmTokenWriter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class NotificationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationsModule {

    @Binds
    abstract fun bindFcmTokenWriter(impl: FirebaseFcmTokenWriter): FcmTokenWriter

    companion object {
        @Provides
        @Singleton
        @NotificationScope
        fun provideNotificationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
