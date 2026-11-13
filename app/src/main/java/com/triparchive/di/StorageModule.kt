package com.triparchive.di

import android.content.Context
import androidx.room.Room
import com.triparchive.data.local.MIGRATION_1_2
import com.triparchive.data.local.MIGRATION_2_3
import com.triparchive.data.local.TripArchiveDatabase
import com.triparchive.data.profile.ProfileDao
import com.triparchive.data.profile.ProfileRepository
import com.triparchive.data.profile.RoomProfileRepository
import com.triparchive.data.record.RecordDao
import com.triparchive.data.record.RecordRepository
import com.triparchive.data.record.RoomRecordRepository
import com.triparchive.data.trip.RoomTripSessionRepository
import com.triparchive.data.trip.TripSessionDao
import com.triparchive.data.trip.TripSessionRepository
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
    ): TripArchiveDatabase =
        Room
            .databaseBuilder(context, TripArchiveDatabase::class.java, "triparchive.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    fun profileDao(database: TripArchiveDatabase): ProfileDao = database.profileDao()

    @Provides
    fun recordDao(database: TripArchiveDatabase): RecordDao = database.recordDao()

    @Provides
    fun tripSessionDao(database: TripArchiveDatabase): TripSessionDao = database.tripSessionDao()

    @Provides
    @Singleton
    fun profileRepository(dao: ProfileDao): ProfileRepository = RoomProfileRepository(dao)

    @Provides
    @Singleton
    fun recordRepository(dao: RecordDao): RecordRepository = RoomRecordRepository(dao)

    @Provides
    @Singleton
    fun tripSessionRepository(
        database: TripArchiveDatabase,
        dao: TripSessionDao,
    ): TripSessionRepository = RoomTripSessionRepository(database, dao)
}
