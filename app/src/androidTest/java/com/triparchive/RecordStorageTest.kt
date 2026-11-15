package com.triparchive

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.triparchive.data.local.MIGRATION_1_2
import com.triparchive.data.local.MIGRATION_2_3
import com.triparchive.data.local.MIGRATION_3_4
import com.triparchive.data.local.MIGRATION_4_5
import com.triparchive.data.local.MIGRATION_5_6
import com.triparchive.data.local.MIGRATION_6_7
import com.triparchive.data.local.TripArchiveDatabase
import com.triparchive.data.profile.ProfileRelationship
import com.triparchive.data.profile.RoomProfileRepository
import com.triparchive.data.record.NewTripRecord
import com.triparchive.data.record.RoomRecordRepository
import com.triparchive.domain.navigation.DestinationCategory
import com.triparchive.domain.record.TripStopDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordStorageTest {
    @Test
    fun recordsAreIsolatedOrderedPersistentAndCascadeOnProfileDeletion() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val name = "records-${System.nanoTime()}.db"

            fun open() =
                Room
                    .databaseBuilder(
                        context,
                        TripArchiveDatabase::class.java,
                        name,
                    ).addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                    ).build()
            var database = open()
            try {
                val profiles = RoomProfileRepository(database.profileDao())
                val first = profiles.create("첫째", ProfileRelationship.PARENT)
                val second = profiles.create("둘째", ProfileRelationship.CHILD)
                var records = RoomRecordRepository(database.recordDao())
                val old =
                    records.saveCompletedRecord(
                        first,
                        NewTripRecord(
                            "이전",
                            1,
                            2,
                            null,
                            listOf("출발", "도착").map {
                                TripStopDraft(it)
                            },
                        ),
                    )
                val newer =
                    records.saveCompletedRecord(
                        first,
                        NewTripRecord(
                            "최신",
                            3,
                            4,
                            12345,
                            listOf(
                                TripStopDraft("하나", DestinationCategory.MART),
                                TripStopDraft("둘", DestinationCategory.FOOD),
                                TripStopDraft("셋", DestinationCategory.HOSPITAL),
                            ),
                        ),
                    )
                val other = records.saveCompletedRecord(second, NewTripRecord("다른 프로필", 1, 2, null, emptyList()))
                database.openHelper.writableDatabase.execSQL(
                    "CREATE TRIGGER fail_stop BEFORE INSERT ON trip_stops BEGIN SELECT RAISE(ABORT, 'stop failed'); END",
                )
                try {
                    records.saveCompletedRecord(
                        first,
                        NewTripRecord("실패", 1, 2, null, listOf("장소").map { TripStopDraft(it) }),
                    )
                    throw AssertionError("Stop insert should fail")
                } catch (_: android.database.SQLException) {
                    // The parent record must roll back together with its stops.
                }
                database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_stop")
                assertEquals(listOf(newer, old), records.observeRecords(first).first().map { it.id })
                assertNull(records.observeRecord(second, newer).first())
                assertEquals(
                    listOf("하나", "둘", "셋"),
                    records
                        .observeRecord(first, newer)
                        .first()!!
                        .stops
                        .map { it.placeName },
                )
                database.close()
                database = open()
                records = RoomRecordRepository(database.recordDao())
                assertEquals(
                    12345L,
                    records
                        .observeRecord(first, newer)
                        .first()!!
                        .record.distanceMeters,
                )
                assertEquals(
                    listOf("MART", "FOOD", "HOSPITAL"),
                    records
                        .observeRecord(first, newer)
                        .first()!!
                        .stops
                        .map { it.category },
                )
                database.profileDao().delete(first)
                assertTrue(records.observeRecords(first).first().isEmpty())
                assertNull(records.observeRecord(first, newer).first())
                assertEquals(
                    other,
                    records
                        .observeRecords(second)
                        .first()
                        .single()
                        .id,
                )
                val remainingStops = database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM trip_stops")
                remainingStops.use {
                    it.moveToFirst()
                    assertEquals(0, it.getInt(0))
                }
            } finally {
                database.close()
                context.deleteDatabase(name)
            }
        }

    @Test
    fun migrationPreservesExistingProfiles() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val name = "records-migration-${System.nanoTime()}.db"
            val path = context.getDatabasePath(name)
            path.parentFile?.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `profiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `nickname` TEXT NOT NULL, `relationship` TEXT NOT NULL)",
                )
                db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
                db.execSQL(
                    "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, 'df8c0ed6c34b9c3efa3ae8719541d53b')",
                )
                db.execSQL("INSERT INTO profiles (id, nickname, relationship) VALUES (42, '준', 'PARENT')")
                db.version = 1
            }
            val database =
                Room
                    .databaseBuilder(
                        context,
                        TripArchiveDatabase::class.java,
                        name,
                    ).addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                    ).build()
            try {
                val profile = database.profileDao().getById(42)
                assertEquals("준", profile?.nickname)
                assertEquals(ProfileRelationship.PARENT, profile?.relationship)
                val repository = RoomRecordRepository(database.recordDao())
                val id =
                    repository.saveCompletedRecord(
                        42,
                        NewTripRecord(
                            "여행",
                            1,
                            2,
                            null,
                            listOf("서울", "부산").map {
                                TripStopDraft(it)
                            },
                        ),
                    )
                assertEquals(
                    2,
                    repository
                        .observeRecord(42, id)
                        .first()!!
                        .stops.size,
                )
            } finally {
                database.close()
                context.deleteDatabase(name)
            }
        }
}
