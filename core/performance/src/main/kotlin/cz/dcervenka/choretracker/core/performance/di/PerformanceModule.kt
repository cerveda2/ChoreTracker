package cz.dcervenka.choretracker.core.performance.di

import cz.dcervenka.choretracker.core.common.performance.PerformanceMonitor
import cz.dcervenka.choretracker.core.performance.FirebasePerformanceMonitor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PerformanceModule {

    @Binds
    abstract fun bindPerformanceMonitor(impl: FirebasePerformanceMonitor): PerformanceMonitor
}
