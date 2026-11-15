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
import com.triparchive.data.trip.TripSessionDao
import com.triparchive.data.trip.TripSessionEntity
import com.triparchive.data.trip.TripSessionPropEntity

@Database(
    entities = [
        Profile::class, TripRecord::class, TripStop::class, TripSessionEntity::class, TripSessionPropEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class TripArchiveDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    abstract fun recordDao(): RecordDao

    abstract fun tripSessionDao(): TripSessionDao
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
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `trip_sessions` (`profileId` INTEGER NOT NULL, `sessionId` TEXT NOT NULL, `lookId` TEXT NOT NULL, `relationship` TEXT NOT NULL, `status` TEXT NOT NULL, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`profileId`), FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
        }
    }

val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `trip_stops` ADD COLUMN `category` TEXT")
        }
    }

val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            createTripSessionProps(db)
        }
    }

val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // An unreleased feature build also used version 5 without the session props table.
            createTripSessionProps(db)
            db.addColumnIfMissing("trip_stops", "latitude", "REAL")
            db.addColumnIfMissing("trip_stops", "longitude", "REAL")
        }
    }

val MIGRATION_6_7 =
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.addColumnIfMissing("profiles", "mapColor", "TEXT NOT NULL DEFAULT 'PINK'")
            db.addColumnIfMissing("trip_stops", "districtCode", "TEXT")
        }
    }

private fun createTripSessionProps(db: SupportSQLiteDatabase) {
    db.execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_trip_sessions_sessionId` ON `trip_sessions` (`sessionId`)",
    )
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `trip_session_props` (`sessionId` TEXT NOT NULL, `category` TEXT NOT NULL, `acquiredOrder` INTEGER NOT NULL, PRIMARY KEY(`sessionId`, `category`), FOREIGN KEY(`sessionId`) REFERENCES `trip_sessions`(`sessionId`) ON UPDATE NO ACTION ON DELETE CASCADE)",
    )
    db.execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_trip_session_props_sessionId_acquiredOrder` ON `trip_session_props` (`sessionId`, `acquiredOrder`)",
    )
}

private fun SupportSQLiteDatabase.addColumnIfMissing(
    table: String,
    column: String,
    definition: String,
) {
    val exists =
        query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) {
                    found = true
                    break
                }
            }
            found
        }
    if (!exists) execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $definition")
}
