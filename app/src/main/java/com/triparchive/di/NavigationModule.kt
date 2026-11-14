package com.triparchive.di

import com.triparchive.data.navigation.KakaoDestinationCategoryClassifier
import com.triparchive.data.navigation.PleosDestinationSource
import com.triparchive.domain.navigation.DestinationCategoryClassifier
import com.triparchive.domain.navigation.DestinationSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NavigationModule {
    @Binds
    abstract fun destinationSource(source: PleosDestinationSource): DestinationSource

    @Binds
    abstract fun categoryClassifier(classifier: KakaoDestinationCategoryClassifier): DestinationCategoryClassifier
}
