package com.triparchive.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.triparchive.data.profile.Profile
import com.triparchive.data.profile.ProfileDao
import com.triparchive.data.trip.TripSessionDao
import com.triparchive.data.trip.TripSessionEntity

@Database(entities = [Profile::class, TripSessionEntity::class], version = 2, exportSchema = true)
abstract class TripArchiveDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    abstract fun tripSessionDao(): TripSessionDao
}

val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `trip_sessions` (
                    `profileId` INTEGER NOT NULL,
                    `sessionId` TEXT NOT NULL,
                    `lookId` TEXT NOT NULL,
                    `relationship` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `updatedAtMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`profileId`),
                    FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
        }
    }
