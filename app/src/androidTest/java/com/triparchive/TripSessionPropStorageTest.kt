package com.triparchive

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.triparchive.data.character.CharacterLookCatalog
import com.triparchive.data.local.MIGRATION_4_5
import com.triparchive.data.local.MIGRATION_5_6
import com.triparchive.data.local.TripArchiveDatabase
import com.triparchive.data.profile.Profile
import com.triparchive.data.profile.ProfileRelationship
import com.triparchive.data.trip.RoomTripSessionRepository
import com.triparchive.domain.navigation.DestinationCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripSessionPropStorageTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun open(name: String) =
        Room
            .databaseBuilder(context, TripArchiveDatabase::class.java, name)
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
            .build()

    @Test
    fun acquisitionOrderSurvivesReopenAndNextTripRejectsOldResponses() =
        runBlocking {
            val name = "props-${System.nanoTime()}.db"
            var db = open(name)
            try {
                val profile = Profile(nickname = "아이", relationship = ProfileRelationship.CHILD)
                val id = db.profileDao().insert(profile)
                var repository = RoomTripSessionRepository(db, db.tripSessionDao())
                val trip = repository.openOrResume(profile.copy(id = id))
                val sequence =
                    listOf(
                        DestinationCategory.FOOD,
                        DestinationCategory.TOURISM,
                        DestinationCategory.FOOD,
                        DestinationCategory.HOSPITAL,
                    )
                sequence.forEach { assertTrue(repository.acquireProp(id, trip.sessionId, it)) }
                assertFalse(repository.acquireProp(id + 1, trip.sessionId, DestinationCategory.MART))
                val expected = sequence.distinct()
                assertEquals(expected, repository.observe(id).first()!!.props)
                assertEquals(expected, repository.openOrResume(profile.copy(id = id)).props)
                db.close()
                db = open(name)
                repository = RoomTripSessionRepository(db, db.tripSessionDao())
                val restored = repository.openOrResume(profile.copy(id = id))
                assertEquals(trip.sessionId, restored.sessionId)
                assertEquals(trip.lookId, restored.lookId)
                assertEquals(expected, restored.props)
                val ready = repository.finish(id, trip.sessionId)!!
                assertTrue(ready.props.isEmpty())
                assertFalse(repository.acquireProp(id, trip.sessionId, DestinationCategory.MART))
                assertFalse(repository.acquireProp(id, ready.sessionId, DestinationCategory.MART))
                val next = repository.openOrResume(profile.copy(id = id))
                assertNotEquals(trip.sessionId, next.sessionId)
                assertTrue(next.props.isEmpty())
                assertTrue(repository.acquireProp(id, next.sessionId, DestinationCategory.FOOD))
                assertEquals(listOf(DestinationCategory.FOOD), repository.observe(id).first()!!.props)
                assertTrue(db.tripSessionDao().getProps(trip.sessionId).isEmpty())
            } finally {
                db.close()
                context.deleteDatabase(name)
            }
        }

    @Test
    fun concurrentRequestsHaveFiveUniqueOrderedSlotsAndFinishIsAtomic() =
        runBlocking {
            val name = "props-concurrent-${System.nanoTime()}.db"
            val db = open(name)
            try {
                val profile = Profile(nickname = "가족", relationship = ProfileRelationship.PARENT)
                val id = db.profileDao().insert(profile)
                val repository = RoomTripSessionRepository(db, db.tripSessionDao())
                val trip = repository.openOrResume(profile.copy(id = id))
                (0 until 100)
                    .map { index ->
                        async(Dispatchers.IO) {
                            repository.acquireProp(id, trip.sessionId, DestinationCategory.entries[index % 5])
                        }
                    }.awaitAll()
                val props = db.tripSessionDao().getProps(trip.sessionId)
                assertEquals(5, props.size)
                assertEquals((0..4).toList(), props.map { it.acquiredOrder })
                assertEquals(5, props.map { it.category }.toSet().size)
                val order = props.map { it.category }
                DestinationCategory.entries.forEach { repository.acquireProp(id, trip.sessionId, it) }
                assertEquals(order, db.tripSessionDao().getProps(trip.sessionId).map { it.category })
                val finish = async(Dispatchers.IO) { repository.finish(id, trip.sessionId) }
                val late =
                    async(Dispatchers.IO) { repository.acquireProp(id, trip.sessionId, DestinationCategory.MART) }
                finish.await()
                late.await()
                assertTrue(
                    repository
                        .observe(id)
                        .first()!!
                        .props
                        .isEmpty(),
                )
                assertFalse(repository.acquireProp(id, trip.sessionId, DestinationCategory.FOOD))
            } finally {
                db.close()
                context.deleteDatabase(name)
            }
        }

    @Test
    fun migrationPreservesActiveTripAndStartsEmptyUntilConfirmedDestinationIsRestored() =
        runBlocking {
            val name = "props-migration-${System.nanoTime()}.db"
            val helper = MigrationTestHelper(instrumentation, TripArchiveDatabase::class.java)
            val look = CharacterLookCatalog.looksFor(ProfileRelationship.CHILD).first().id
            helper.createDatabase(name, 4).use { db ->
                db.execSQL("INSERT INTO profiles (id, nickname, relationship) VALUES (7, '아이', 'CHILD')")
                db.execSQL("INSERT INTO trip_sessions VALUES (7, 'old-session', '$look', 'CHILD', 'ACTIVE', 1)")
                db.execSQL("INSERT INTO trip_records VALUES (1, 7, '기록', 1, 2, NULL)")
                db.execSQL("INSERT INTO trip_stops VALUES (1, 1, 0, '시장', 'MART')")
            }
            helper.runMigrationsAndValidate(name, 5, true, MIGRATION_4_5).use { db ->
                db.query("SELECT category FROM trip_stops").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("MART", cursor.getString(0))
                }
            }
            val db = open(name)
            try {
                val repository = RoomTripSessionRepository(db, db.tripSessionDao())
                val trip = repository.openOrResume(Profile(7, "아이", ProfileRelationship.CHILD))
                assertEquals("old-session", trip.sessionId)
                assertEquals(look, trip.lookId)
                assertTrue(trip.props.isEmpty())
                assertTrue(repository.acquireProp(7, trip.sessionId, DestinationCategory.MART))
                assertTrue(repository.acquireProp(7, trip.sessionId, DestinationCategory.MART))
                assertEquals(listOf(DestinationCategory.MART), repository.observe(7).first()!!.props)
                db.openHelper.writableDatabase.execSQL("DELETE FROM profiles WHERE id = 7")
                assertTrue(db.tripSessionDao().getProps(trip.sessionId).isEmpty())
            } finally {
                db.close()
                context.deleteDatabase(name)
            }
        }
}
