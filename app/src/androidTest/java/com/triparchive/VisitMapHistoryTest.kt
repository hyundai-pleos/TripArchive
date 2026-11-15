package com.triparchive

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.triparchive.data.local.TripArchiveDatabase
import com.triparchive.data.profile.ProfileRelationship
import com.triparchive.data.profile.RoomProfileRepository
import com.triparchive.data.record.NewTripRecord
import com.triparchive.data.repository.map.BoundaryRepositoryImpl
import com.triparchive.data.repository.map.RegionVisitCountRepositoryImpl
import com.triparchive.domain.record.TripStopDraft
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class VisitMapHistoryTest {
    @Test
    fun completedArrivalsUpdateAnActiveMapSubscription() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val database = Room.inMemoryDatabaseBuilder(context, TripArchiveDatabase::class.java).build()
            try {
                val profile = RoomProfileRepository(database.profileDao()).create("아이", ProfileRelationship.CHILD)
                val dao = database.recordDao()
                val boundaries = BoundaryRepositoryImpl(context).getBoundaries()
                val counts = RegionVisitCountRepositoryImpl(dao).observeDistrictVisitCounts(boundaries.districts)
                val updates = Channel<Map<String, Int>>(Channel.UNLIMITED)
                val observer = launch(start = CoroutineStart.UNDISPATCHED) { counts.collect { updates.send(it) } }
                try {
                    withTimeout(10_000) {
                        assertEquals(emptyMap<String, Int>(), updates.receive())
                        val stop = TripStopDraft("경복궁", latitude = 37.5796, longitude = 126.9770)
                        dao.insertCompletedRecord(
                            profile,
                            NewTripRecord("서울 여행", 1, 2, null, listOf(stop, TripStopDraft("좌표 없는 장소"))),
                        )
                        assertEquals(mapOf("11110" to 1), updates.receive())
                        dao.insertCompletedRecord(profile, NewTripRecord("다시 서울", 3, 4, null, listOf(stop)))
                        assertEquals(mapOf("11110" to 2), updates.receive())
                    }
                } finally {
                    observer.cancelAndJoin()
                }
            } finally {
                database.close()
            }
        }
}
