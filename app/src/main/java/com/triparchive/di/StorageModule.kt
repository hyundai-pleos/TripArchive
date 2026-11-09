package com.triparchive.di

import android.content.Context
import androidx.room.Room
import com.triparchive.data.local.TripArchiveDatabase
import com.triparchive.data.profile.ProfileDao
import com.triparchive.data.profile.ProfileRepository
import com.triparchive.data.profile.RoomProfileRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): TripArchiveDatabase = Room.databaseBuilder(context, TripArchiveDatabase::class.java, "triparchive.db").build()

    @Provides
    fun profileDao(database: TripArchiveDatabase): ProfileDao = database.profileDao()

    @Provides
    @Singleton
    fun profileRepository(dao: ProfileDao): ProfileRepository = RoomProfileRepository(dao)
}
