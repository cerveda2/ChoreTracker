package cz.dcervenka.choretracker.core.remote.firebase.di

import cz.dcervenka.choretracker.core.remote.contract.RemoteAuthDataSource
import cz.dcervenka.choretracker.core.remote.contract.RemoteHouseholdDataSource
import cz.dcervenka.choretracker.core.remote.firebase.datasource.FirebaseAuthDataSource
import cz.dcervenka.choretracker.core.remote.firebase.datasource.FirebaseHouseholdDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class FirebaseRemoteDataSourceModule {
    @Binds
    abstract fun bindRemoteAuthDataSource(impl: FirebaseAuthDataSource): RemoteAuthDataSource

    @Binds
    abstract fun bindRemoteHouseholdDataSource(impl: FirebaseHouseholdDataSource): RemoteHouseholdDataSource
}
