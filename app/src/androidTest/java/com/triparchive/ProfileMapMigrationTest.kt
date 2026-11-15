package com.triparchive

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.triparchive.data.local.MIGRATION_3_4
import com.triparchive.data.local.MIGRATION_4_5
import com.triparchive.data.local.MIGRATION_5_6
import com.triparchive.data.local.MIGRATION_6_7
import com.triparchive.data.local.TripArchiveDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileMapMigrationTest {
    @Test
    fun existingProfilesAndTripsSurviveAndReceiveSafeDefaults() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "profile-map-migration-${System.nanoTime()}.db"
        val helper = MigrationTestHelper(instrumentation, TripArchiveDatabase::class.java)
        helper.createDatabase(name, 4).use { db ->
            db.execSQL("INSERT INTO profiles (id, nickname, relationship) VALUES (7, '준', 'PARENT')")
            db.execSQL(
                "INSERT INTO trip_records (id, profileId, title, startedAt, endedAt, distanceMeters) " +
                    "VALUES (11, 7, '여행', 1, 2, NULL)",
            )
            db.execSQL(
                "INSERT INTO trip_stops (id, tripId, position, placeName, category) " +
                    "VALUES (13, 11, 0, '시장', 'MART')",
            )
            db.execSQL(
                "INSERT INTO trip_sessions (profileId, sessionId, lookId, relationship, status, updatedAtMillis) " +
                    "VALUES (7, 'session', 'look', 'PARENT', 'ACTIVE', 1)",
            )
        }
        try {
            helper.runMigrationsAndValidate(name, 7, true, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).use { db ->
                db.query("SELECT nickname, mapColor FROM profiles WHERE id = 7").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("준", cursor.getString(0))
                    assertEquals("PINK", cursor.getString(1))
                }
                db.query("SELECT placeName, category, districtCode FROM trip_stops WHERE id = 13").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("시장", cursor.getString(0))
                    assertEquals("MART", cursor.getString(1))
                    assertTrue(cursor.isNull(2))
                }
                db.query("SELECT sessionId, lookId FROM trip_sessions WHERE profileId = 7").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("session", cursor.getString(0))
                    assertEquals("look", cursor.getString(1))
                }
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migrationFromVersionThreeRetainsExistingStop() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "profile-map-migration-chain-${System.nanoTime()}.db"
        val helper = MigrationTestHelper(instrumentation, TripArchiveDatabase::class.java)
        helper.createDatabase(name, 3).use { db ->
            db.execSQL("INSERT INTO profiles (id, nickname, relationship) VALUES (7, '준', 'PARENT')")
            db.execSQL(
                "INSERT INTO trip_records (id, profileId, title, startedAt, endedAt, distanceMeters) " +
                    "VALUES (11, 7, '여행', 1, 2, NULL)",
            )
            db.execSQL("INSERT INTO trip_stops (id, tripId, position, placeName) VALUES (13, 11, 0, '시장')")
        }
        try {
            helper
                .runMigrationsAndValidate(
                    name,
                    7,
                    true,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                ).use { db ->
                    db.query("SELECT category, districtCode FROM trip_stops WHERE id = 13").use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertTrue(cursor.isNull(0))
                        assertTrue(cursor.isNull(1))
                    }
                }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun unreleasedVersionFiveRetainsColorsAndDistricts() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "profile-map-branch-migration-${System.nanoTime()}.db"
        val helper = MigrationTestHelper(instrumentation, TripArchiveDatabase::class.java)
        helper.createDatabase(name, 4).use { db ->
            db.execSQL("ALTER TABLE profiles ADD COLUMN mapColor TEXT NOT NULL DEFAULT 'PINK'")
            db.execSQL("ALTER TABLE trip_stops ADD COLUMN districtCode TEXT")
            db.execSQL("INSERT INTO profiles (id, nickname, relationship, mapColor) VALUES (7, '준', 'PARENT', 'BLUE')")
            db.execSQL(
                "INSERT INTO trip_records (id, profileId, title, startedAt, endedAt, distanceMeters) " +
                    "VALUES (11, 7, '여행', 1, 2, NULL)",
            )
            db.execSQL(
                "INSERT INTO trip_stops (id, tripId, position, placeName, districtCode) " +
                    "VALUES (13, 11, 0, '시장', '11110')",
            )
            db.version = 5
        }
        try {
            helper.runMigrationsAndValidate(name, 7, true, MIGRATION_5_6, MIGRATION_6_7).use { db ->
                db.query("SELECT mapColor FROM profiles WHERE id = 7").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("BLUE", cursor.getString(0))
                }
                db.query("SELECT districtCode, latitude, longitude FROM trip_stops WHERE id = 13").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("11110", cursor.getString(0))
                    assertTrue(cursor.isNull(1))
                    assertTrue(cursor.isNull(2))
                }
                db.query("SELECT COUNT(*) FROM trip_session_props").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun versionSixRetainsCoordinatesAndSessionProps() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "profile-map-main-migration-${System.nanoTime()}.db"
        val helper = MigrationTestHelper(instrumentation, TripArchiveDatabase::class.java)
        helper.createDatabase(name, 6).use { db ->
            db.execSQL("INSERT INTO profiles (id, nickname, relationship) VALUES (7, '준', 'PARENT')")
            db.execSQL(
                "INSERT INTO trip_records (id, profileId, title, startedAt, endedAt, distanceMeters) " +
                    "VALUES (11, 7, '여행', 1, 2, NULL)",
            )
            db.execSQL(
                "INSERT INTO trip_stops (id, tripId, position, placeName, latitude, longitude) " +
                    "VALUES (13, 11, 0, '시장', 37.5, 127.0)",
            )
            db.execSQL(
                "INSERT INTO trip_sessions (profileId, sessionId, lookId, relationship, status, updatedAtMillis) " +
                    "VALUES (7, 'session', 'look', 'PARENT', 'ACTIVE', 1)",
            )
            db.execSQL(
                "INSERT INTO trip_session_props (sessionId, category, acquiredOrder) VALUES ('session', 'MART', 1)",
            )
        }
        try {
            helper.runMigrationsAndValidate(name, 7, true, MIGRATION_6_7).use { db ->
                db.query("SELECT latitude, longitude, districtCode FROM trip_stops WHERE id = 13").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(37.5, cursor.getDouble(0), 0.0)
                    assertEquals(127.0, cursor.getDouble(1), 0.0)
                    assertTrue(cursor.isNull(2))
                }
                db.query("SELECT category FROM trip_session_props WHERE sessionId = 'session'").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("MART", cursor.getString(0))
                }
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
