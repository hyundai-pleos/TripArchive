package com.triparchive.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.triparchive.data.profile.Profile
import com.triparchive.data.profile.ProfileDao

@Database(entities = [Profile::class], version = 1, exportSchema = true)
abstract class TripArchiveDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
}
