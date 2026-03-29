package cz.dcervenka.choretracker.core.data.di

import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.data.contract.ChoreCompletionRepository
import cz.dcervenka.choretracker.core.data.contract.ChoreRepository
import cz.dcervenka.choretracker.core.data.contract.HouseholdRepository
import cz.dcervenka.choretracker.core.data.contract.StatsRepository
import cz.dcervenka.choretracker.core.data.repository.OfflineFirstChoreCompletionRepository
import cz.dcervenka.choretracker.core.data.repository.OfflineFirstChoreRepository
import cz.dcervenka.choretracker.core.data.repository.OfflineFirstHouseholdRepository
import cz.dcervenka.choretracker.core.data.repository.OfflineFirstStatsRepository
import cz.dcervenka.choretracker.core.data.repository.PreviewAwareAuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

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
}
