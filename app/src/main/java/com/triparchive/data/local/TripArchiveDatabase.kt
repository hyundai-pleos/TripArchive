package com.triparchive.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.triparchive.data.profile.Profile
import com.triparchive.data.profile.ProfileDao
import com.triparchive.data.record.RecordDao
import com.triparchive.data.record.TripRecord
import com.triparchive.data.record.TripStop

@Database(entities = [Profile::class, TripRecord::class, TripStop::class], version = 3, exportSchema = true)
abstract class TripArchiveDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    abstract fun recordDao(): RecordDao
}

val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `trip_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `profileId` INTEGER NOT NULL, `title` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL, `distanceMeters` INTEGER, FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_records_profileId` ON `trip_records` (`profileId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `trip_stops` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tripId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `placeName` TEXT NOT NULL, FOREIGN KEY(`tripId`) REFERENCES `trip_records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_trip_stops_tripId_position` ON `trip_stops` (`tripId`, `position`)",
            )
        }
    }

val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `trip_stops` ADD COLUMN `category` TEXT")
        }
    }
