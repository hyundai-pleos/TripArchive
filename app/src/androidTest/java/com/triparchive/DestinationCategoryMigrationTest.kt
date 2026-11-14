package com.triparchive

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.triparchive.data.local.MIGRATION_3_4
import com.triparchive.data.local.TripArchiveDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationCategoryMigrationTest {
    @Test
    fun migrationFromMainPreservesRecordsAndCharacterSession() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "destination-category-migration-${System.nanoTime()}.db"
        val helper = MigrationTestHelper(instrumentation, TripArchiveDatabase::class.java)
        helper.createDatabase(name, 3).use { db ->
            db.execSQL("INSERT INTO profiles (id, nickname, relationship) VALUES (7, '준', 'PARENT')")
            db.execSQL(
                "INSERT INTO trip_records (id, profileId, title, startedAt, endedAt, distanceMeters) " +
                    "VALUES (1, 7, '시장 여행', 1, 2, NULL)",
            )
            db.execSQL("INSERT INTO trip_stops (id, tripId, position, placeName) VALUES (1, 1, 0, '시장')")
            db.execSQL(
                "INSERT INTO trip_sessions (profileId, sessionId, lookId, relationship, status, updatedAtMillis) " +
                    "VALUES (7, 'existing-session', 'existing-look', 'PARENT', 'ACTIVE', 1)",
            )
        }
        try {
            helper.runMigrationsAndValidate(name, 4, true, MIGRATION_3_4).use { db ->
                db.query("SELECT placeName, category FROM trip_stops WHERE id = 1").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("시장", cursor.getString(0))
                    assertTrue(cursor.isNull(1))
                }
                db.query("SELECT sessionId, lookId, status FROM trip_sessions WHERE profileId = 7").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("existing-session", cursor.getString(0))
                    assertEquals("existing-look", cursor.getString(1))
                    assertEquals("ACTIVE", cursor.getString(2))
                }
                db.execSQL("UPDATE trip_stops SET category = 'MART' WHERE id = 1")
                db.query("SELECT category FROM trip_stops WHERE id = 1").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("MART", cursor.getString(0))
                }
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
