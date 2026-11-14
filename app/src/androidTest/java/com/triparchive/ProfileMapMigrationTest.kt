package com.triparchive

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.triparchive.data.local.MIGRATION_3_4
import com.triparchive.data.local.MIGRATION_4_5
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
            helper.runMigrationsAndValidate(name, 5, true, MIGRATION_4_5).use { db ->
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
            helper.runMigrationsAndValidate(name, 5, true, MIGRATION_3_4, MIGRATION_4_5).use { db ->
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
}
