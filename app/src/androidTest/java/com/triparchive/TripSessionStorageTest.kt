package com.triparchive

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.triparchive.data.local.MIGRATION_1_2
import com.triparchive.data.local.MIGRATION_2_3
import com.triparchive.data.local.MIGRATION_3_4
import com.triparchive.data.local.MIGRATION_4_5
import com.triparchive.data.local.MIGRATION_5_6
import com.triparchive.data.local.TripArchiveDatabase
import com.triparchive.data.profile.Profile
import com.triparchive.data.profile.ProfileRelationship
import com.triparchive.data.trip.RoomTripSessionRepository
import com.triparchive.data.trip.TripSessionStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripSessionStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun sessionsRestoreAfterDatabaseReopenAndCompletionChangesLookOnlyOnce() =
        runBlocking {
            val name = "trip-session-test-${System.nanoTime()}.db"
            var database = openDatabase(name)
            try {
                val child = Profile(nickname = "아이", relationship = ProfileRelationship.CHILD)
                val adult = Profile(nickname = "연인", relationship = ProfileRelationship.PARTNER)
                val childId = database.profileDao().insert(child)
                val adultId = database.profileDao().insert(adult)
                val childProfile = child.copy(id = childId)
                val adultProfile = adult.copy(id = adultId)
                var repository = RoomTripSessionRepository(database, database.tripSessionDao())

                val firstTrip = repository.openOrResume(childProfile)
                val sameTrip = repository.openOrResume(childProfile)
                val adultTrip = repository.openOrResume(adultProfile)
                assertEquals(firstTrip.sessionId, sameTrip.sessionId)
                assertEquals(firstTrip.lookId, sameTrip.lookId)
                assertEquals(TripSessionStatus.ACTIVE, firstTrip.status)
                assertNotEquals(firstTrip.lookId, adultTrip.lookId)
                assertTrue(firstTrip.lookId.startsWith("child_"))
                assertTrue(adultTrip.lookId.startsWith("partner_"))

                val nextTrip = repository.finish(childId, firstTrip.sessionId)
                assertNotNull(nextTrip)
                assertEquals(TripSessionStatus.READY, nextTrip?.status)
                assertNotEquals(firstTrip.sessionId, nextTrip?.sessionId)
                assertNotEquals(firstTrip.lookId, nextTrip?.lookId)
                assertNull(repository.finish(childId, firstTrip.sessionId))

                database.close()
                database = openDatabase(name)
                repository = RoomTripSessionRepository(database, database.tripSessionDao())
                val restoredNextTrip = repository.openOrResume(childProfile)
                assertEquals(nextTrip?.sessionId, restoredNextTrip.sessionId)
                assertEquals(nextTrip?.lookId, restoredNextTrip.lookId)
                assertEquals(TripSessionStatus.ACTIVE, restoredNextTrip.status)
                val restoredAdultTrip = repository.openOrResume(adultProfile)
                assertEquals(adultTrip.sessionId, restoredAdultTrip.sessionId)
                assertEquals(adultTrip.lookId, restoredAdultTrip.lookId)
            } finally {
                database.close()
                context.deleteDatabase(name)
            }
        }

    @Test
    fun changingRelationshipBeforeReopeningAReadySessionStillStartsActiveTrip() =
        runBlocking {
            val name = "trip-session-relationship-test-${System.nanoTime()}.db"
            val database = openDatabase(name)
            try {
                val child = Profile(nickname = "프로필", relationship = ProfileRelationship.CHILD)
                val profileId = database.profileDao().insert(child)
                val repository = RoomTripSessionRepository(database, database.tripSessionDao())
                val firstTrip = repository.openOrResume(child.copy(id = profileId))
                val finishedTrip = checkNotNull(repository.finish(profileId, firstTrip.sessionId))

                val resumed =
                    repository.openOrResume(
                        child.copy(id = profileId, relationship = ProfileRelationship.PARTNER),
                    )

                assertEquals(finishedTrip.sessionId, resumed.sessionId)
                assertEquals(TripSessionStatus.ACTIVE, resumed.status)
                assertEquals(ProfileRelationship.PARTNER.name, resumed.relationship)
                assertTrue(resumed.lookId.startsWith("partner_"))
            } finally {
                database.close()
                context.deleteDatabase(name)
            }
        }

    @Test
    fun migrationPreservesProfilesAndCreatesAnEmptySessionTable() {
        val name = "trip-session-migration-${System.nanoTime()}.db"
        val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TripArchiveDatabase::class.java)
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO profiles (id, nickname, relationship) VALUES (7, '보존 프로필', 'PARENT')")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(name, 3, true, MIGRATION_1_2, MIGRATION_2_3)
        try {
            migrated.query("SELECT nickname, relationship FROM profiles WHERE id = 7").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("보존 프로필", cursor.getString(0))
                assertEquals("PARENT", cursor.getString(1))
            }
            migrated.query("SELECT COUNT(*) FROM trip_sessions").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    private fun openDatabase(name: String) =
        Room
            .databaseBuilder(context, TripArchiveDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()
}
