package com.triparchive

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.triparchive.data.local.TripArchiveDatabase
import com.triparchive.data.profile.ProfileMapColor
import com.triparchive.data.profile.ProfileRelationship
import com.triparchive.data.profile.RoomProfileRepository
import com.triparchive.data.record.RoomRecordRepository
import com.triparchive.data.repository.map.BoundaryRepositoryImpl
import com.triparchive.data.repository.map.RegionVisitCountRepositoryImpl
import com.triparchive.domain.map.GetAdministrativeBoundaries
import com.triparchive.domain.map.ObserveMapVisitIntensities
import com.triparchive.domain.map.ResolveDistricts
import com.triparchive.domain.map.VisitIntensity
import com.triparchive.domain.navigation.CategoryLookupStatus
import com.triparchive.domain.navigation.Destination
import com.triparchive.domain.navigation.DestinationCategory
import com.triparchive.domain.navigation.DestinationState
import com.triparchive.domain.navigation.ResolveDestinationCategory
import com.triparchive.presentation.home.TripSessionViewModel
import com.triparchive.testing.RecordingTripSessions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitMapStorageIntegrationTest {
    @Test
    fun savedArrivalsAreCountedPerProfileAndColorsRemainIndependent() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val db = Room.inMemoryDatabaseBuilder(context, TripArchiveDatabase::class.java).build()
            try {
                val profiles = RoomProfileRepository(db.profileDao())
                val firstProfile = profiles.create("첫 프로필", ProfileRelationship.PARENT)
                val secondProfile = profiles.create("둘째 프로필", ProfileRelationship.CHILD)
                assertTrue(profiles.updateMapColor(firstProfile, ProfileMapColor.BLUE))
                assertEquals("BLUE", profiles.getById(firstProfile)?.mapColor)
                assertEquals("PINK", profiles.getById(secondProfile)?.mapColor)

                val boundaryLoader = GetAdministrativeBoundaries(BoundaryRepositoryImpl(context))
                val resolver = ResolveDistricts(boundaryLoader)
                val recordRepository = RoomRecordRepository(db.recordDao())
                val firstTrip =
                    TripSessionViewModel(
                        recordRepository,
                        FakeDestinationSource(MutableStateFlow(DestinationState())),
                        ResolveDestinationCategory(),
                        resolver,
                        RecordingTripSessions(),
                    )
                val counts = RegionVisitCountRepositoryImpl(db.recordDao(), resolver)
                firstTrip.recordArrival(destination(1, "성심당 DCC점", 36.37556666, 127.39208333))
                firstTrip.recordArrival(destination(2, "다른 유성구 목적지", 36.37556666, 127.39208333))
                firstTrip.recordArrival(destination(3, "서울시청", 37.5665, 126.9780))
                firstTrip.recordArrival(destination(4, "위치 없음", 0.0, 0.0))
                assertEquals(emptyMap<String, Int>(), counts.observeDistrictVisitCounts(firstProfile).first())
                assertTrue(firstTrip.save(firstProfile, null))
                assertEquals(mapOf("30200" to 2, "11140" to 1), counts.observeDistrictVisitCounts(firstProfile).first())
                assertEquals(emptyMap<String, Int>(), counts.observeDistrictVisitCounts(secondProfile).first())

                val secondTrip =
                    TripSessionViewModel(
                        recordRepository,
                        FakeDestinationSource(MutableStateFlow(DestinationState())),
                        ResolveDestinationCategory(),
                        resolver,
                        RecordingTripSessions(),
                    )
                secondTrip.recordArrival(destination(5, "성심당 DCC점", 36.37556666, 127.39208333))
                assertTrue(secondTrip.save(secondProfile, null))
                assertEquals(mapOf("30200" to 1), counts.observeDistrictVisitCounts(secondProfile).first())

                val intensities = ObserveMapVisitIntensities(counts)(boundaryLoader(), firstProfile).first()
                assertEquals(VisitIntensity.SECOND, intensities["30200"])
                assertEquals(VisitIntensity.FIRST, intensities["11140"])
            } finally {
                db.close()
            }
        }

    private fun destination(
        id: Long,
        name: String,
        latitude: Double,
        longitude: Double,
    ) = Destination(id, name, latitude, longitude, DestinationCategory.FOOD, CategoryLookupStatus.SUCCESS)
}
