package com.triparchive.di

import com.triparchive.data.repository.map.BoundaryRepositoryImpl
import com.triparchive.data.repository.map.RegionVisitCountRepositoryImpl
import com.triparchive.domain.map.BoundaryRepository
import com.triparchive.domain.map.RegionVisitCountRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindBoundaryRepository(repository: BoundaryRepositoryImpl): BoundaryRepository

    @Binds
    @Singleton
    abstract fun bindRegionVisitCountRepository(repository: RegionVisitCountRepositoryImpl): RegionVisitCountRepository
}
