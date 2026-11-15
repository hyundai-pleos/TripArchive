package com.triparchive.presentation.home

import com.triparchive.data.navigation.DestinationSession
import com.triparchive.data.record.NewTripRecord
import com.triparchive.data.record.RecordRepository
import com.triparchive.data.record.TripRecord
import com.triparchive.data.record.TripRecordDetail
import com.triparchive.domain.map.AdministrativeBoundaries
import com.triparchive.domain.map.AdministrativeBoundary
import com.triparchive.domain.map.BoundaryPolygon
import com.triparchive.domain.map.BoundaryRepository
import com.triparchive.domain.map.GeoPoint
import com.triparchive.domain.map.GetAdministrativeBoundaries
import com.triparchive.domain.map.ResolveDistricts
import com.triparchive.domain.navigation.CategoryLookupResult
import com.triparchive.domain.navigation.CategoryLookupStatus
import com.triparchive.domain.navigation.Destination
import com.triparchive.domain.navigation.DestinationCategory
import com.triparchive.domain.navigation.DestinationSource
import com.triparchive.domain.navigation.DestinationState
import com.triparchive.domain.navigation.ResolveDestinationCategory
import com.triparchive.presentation.home.components.CategoryChoice
import com.triparchive.testing.RecordingTripSessions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripSessionViewModelTest {
    private fun emptyDistricts() =
        ResolveDistricts(
            GetAdministrativeBoundaries(
                object : BoundaryRepository {
                    override suspend fun getBoundaries() = AdministrativeBoundaries(emptyList(), emptyList())
                },
            ),
        )

    @Test
    fun arrivalsRetainOrderIncludingRepeatedNamesAndCorrectProfile() =
        runBlocking {
            val repository = FakeRecords()
            val session =
                TripSessionViewModel(
                    repository,
                    FakeDestinationSource(),
                    ResolveDestinationCategory(),
                    emptyDistricts(),
                    RecordingTripSessions(),
                )
            session.recordArrival(place(1, "시장"))
            session.recordArrival(place(2, "해변"))
            session.recordArrival(place(3, "시장"))
            assertTrue(session.save(13, null))
            val (profile, record) = repository.saved.single()
            assertEquals(13L, profile)
            assertEquals(listOf("시장", "해변", "시장"), record.stops.map { it.placeName })
            assertNull(record.distanceMeters)
            assertTrue(record.startedAt <= record.endedAt)
            assertTrue(session.save(13, null))
            assertEquals(1, repository.saved.size)
        }

    @Test
    fun pendingArrivalIsIncludedOnFinishAndEmptyTripIsNotSaved() =
        runBlocking {
            val repository = FakeRecords()
            val session =
                TripSessionViewModel(
                    repository,
                    FakeDestinationSource(),
                    ResolveDestinationCategory(),
                    emptyDistricts(),
                    RecordingTripSessions(),
                )
            session.recordArrival(place(1, "시장"))
            assertTrue(session.save(3, place(2, "  해변  ")))
            assertEquals(
                listOf("시장", "해변"),
                repository.saved
                    .single()
                    .second.stops
                    .map { it.placeName },
            )
            assertTrue(
                TripSessionViewModel(
                    repository,
                    FakeDestinationSource(),
                    ResolveDestinationCategory(),
                    emptyDistricts(),
                    RecordingTripSessions(),
                ).save(3, null),
            )
            assertEquals(1, repository.saved.size)
        }

    @Test
    fun failedSaveCanRetryWithoutDuplicatingPendingArrival() =
        runBlocking {
            val repository = FakeRecords().apply { shouldFail = true }
            val session =
                TripSessionViewModel(
                    repository,
                    FakeDestinationSource(),
                    ResolveDestinationCategory(),
                    emptyDistricts(),
                    RecordingTripSessions(),
                )
            session.recordArrival(place(1, "시장"))
            assertFalse(session.save(1, place(2, "해변")))
            assertTrue(repository.saved.isEmpty())
            repository.shouldFail = false
            assertTrue(session.save(1, place(2, "해변")))
            assertEquals(
                listOf("시장", "해변"),
                repository.saved
                    .single()
                    .second.stops
                    .map { it.placeName },
            )
        }

    @Test
    fun concurrentFinishDoesNotCreateTwoRecords() =
        runBlocking {
            val repository = FakeRecords()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            repository.beforeSave = {
                entered.complete(Unit)
                release.await()
            }
            val session =
                TripSessionViewModel(
                    repository,
                    FakeDestinationSource(),
                    ResolveDestinationCategory(),
                    emptyDistricts(),
                    RecordingTripSessions(),
                )
            session.recordArrival(place(1, "시장"))
            val first = async { session.save(1, null) }
            entered.await()
            assertFalse(session.save(1, null))
            release.complete(Unit)
            assertTrue(first.await())
            assertTrue(session.save(1, null))
            assertEquals(1, repository.saved.size)
        }

    @Test
    fun categorizedArrivalsAndPendingArrivalKeepTheirOrder() =
        runBlocking {
            val repository = FakeRecords()
            val session =
                TripSessionViewModel(
                    repository,
                    FakeDestinationSource(),
                    ResolveDestinationCategory(),
                    emptyDistricts(),
                    RecordingTripSessions(),
                )
            session.recordArrival(place(1, "시장", DestinationCategory.MART))
            session.recordArrival(place(2, "병원", DestinationCategory.HOSPITAL))
            assertTrue(session.save(1, place(3, "학교", DestinationCategory.SCHOOL)))
            val record = repository.saved.single().second
            assertEquals(listOf("시장", "병원", "학교"), record.stops.map { it.placeName })
            assertEquals(listOf("MART", "HOSPITAL", "SCHOOL"), record.stops.map { it.category?.name })
        }

    @Test
    fun fallbackCategoryIsKeptAcrossSaveRetries() =
        runBlocking {
            val repository = FakeRecords().apply { shouldFail = true }
            val session =
                TripSessionViewModel(
                    repository,
                    FakeDestinationSource(),
                    ResolveDestinationCategory(),
                    emptyDistricts(),
                    RecordingTripSessions(),
                )
            session.recordArrival(place(1, "시장"))
            assertFalse(session.save(1, place(2, "해변")))
            val firstAttempt = repository.attempts.single()
            repository.shouldFail = false
            assertTrue(session.save(1, place(2, "해변")))
            assertEquals(
                firstAttempt.stops,
                repository.saved
                    .single()
                    .second.stops,
            )
            val stops =
                repository.saved
                    .single()
                    .second.stops
            assertTrue(stops.all { it.category != null })
            assertEquals(listOf("시장", "해변"), stops.map { it.placeName })
        }

    @Test
    fun repeatedConfirmationAndPendingSaveUseVisitIdInsteadOfName() =
        runBlocking {
            val repository = FakeRecords()
            val source = FakeDestinationSource()
            val session =
                TripSessionViewModel(
                    repository,
                    source,
                    ResolveDestinationCategory(),
                    emptyDistricts(),
                    RecordingTripSessions(),
                )
            val first = place(1, "시장", DestinationCategory.MART)
            session.recordArrival(first)
            session.recordArrival(first)
            assertTrue(session.save(1, place(2, "시장", DestinationCategory.FOOD)))
            assertEquals(
                listOf(
                    DestinationCategory.MART,
                    DestinationCategory.FOOD,
                ),
                repository.saved.single().second.stops.map {
                    it.category
                },
            )
            assertEquals(listOf(1L, 1L, 2L), source.acknowledged)
        }

    @Test
    fun alreadyRecordedPendingVisitIsNotAddedAgainOnFinish() =
        runBlocking {
            val repository = FakeRecords()
            val session =
                TripSessionViewModel(
                    repository,
                    FakeDestinationSource(),
                    ResolveDestinationCategory(),
                    emptyDistricts(),
                    RecordingTripSessions(),
                )
            val market = place(1, "시장", DestinationCategory.MART)
            session.recordArrival(market)
            assertTrue(session.save(1, market))
            assertEquals(
                1,
                repository.saved
                    .single()
                    .second.stops.size,
            )
        }

    @Test
    fun finishingIncludesAllArrivalsAwaitingConfirmationInOrder() =
        runBlocking {
            val source = FakeDestinationSource()
            val repository = FakeRecords()
            val session =
                TripSessionViewModel(
                    repository,
                    source,
                    ResolveDestinationCategory(),
                    emptyDistricts(),
                    RecordingTripSessions(),
                )
            source.session.destinationChanged("시장", 37.0, 127.0)
            source.session.arrive("시장")
            source.session.routeStarted()
            source.session.destinationChanged("병원", 37.1, 127.1)
            source.session.arrive("병원")
            val first = source.session.state.pendingArrival!!
            assertTrue(session.save(1, first))
            val stops =
                repository.saved
                    .single()
                    .second.stops
            assertEquals(listOf("시장", "병원"), stops.map { it.placeName })
            assertTrue(stops.all { it.category != null })
            assertTrue(source.pendingArrivals().isEmpty())
        }

    @Test
    fun manualCategoryOverridesLaterClassificationForRecordedArrival() =
        runBlocking {
            val repository = FakeRecords()
            val session =
                TripSessionViewModel(
                    repository,
                    FakeDestinationSource(),
                    ResolveDestinationCategory(),
                    emptyDistricts(),
                    RecordingTripSessions(),
                )
            val destination = place(8, "시장").copy(categoryStatus = CategoryLookupStatus.SUCCESS)
            session.categorySelection.update(DestinationState(current = destination, routing = true))
            session.categorySelection.select(CategoryChoice.SCHOOL)
            session.categorySelection.confirm()
            session.recordArrival(destination.copy(category = DestinationCategory.HOSPITAL))
            assertTrue(session.save(1, null))
            val saved = repository.saved.single().second
            assertEquals(DestinationCategory.SCHOOL, saved.stops.single().category)
        }

    @Test
    fun manualCategoryIsAppliedWhenFinishingWithSourcePendingArrival() =
        runBlocking {
            val repository = FakeRecords()
            val source = FakeDestinationSource()
            val session =
                TripSessionViewModel(
                    repository,
                    source,
                    ResolveDestinationCategory(),
                    emptyDistricts(),
                    RecordingTripSessions(),
                )
            source.session.routeStarted()
            source.session.destinationChanged("시장", 37.0, 127.0)
            val current = source.session.state.current!!
            val id = current.id
            source.session.categoryResolved(id, CategoryLookupResult(null, CategoryLookupStatus.SUCCESS))
            session.categorySelection.update(source.session.state)
            session.categorySelection.select(CategoryChoice.FOOD)
            session.categorySelection.confirm()
            source.session.arrive("시장")
            source.session.categoryResolved(
                id,
                CategoryLookupResult(DestinationCategory.HOSPITAL, CategoryLookupStatus.SUCCESS),
            )
            assertTrue(session.save(1, null))
            val saved = repository.saved.single().second
            assertEquals(DestinationCategory.FOOD, saved.stops.single().category)
        }

    @Test
    fun otherKeepsAnUnclassifiedCategoryAcrossSaveRetries() =
        runBlocking {
            val repository = FakeRecords().apply { shouldFail = true }
            val session =
                TripSessionViewModel(
                    repository,
                    FakeDestinationSource(),
                    ResolveDestinationCategory(),
                    emptyDistricts(),
                    RecordingTripSessions(),
                )
            val destination = place(8, "장소").copy(categoryStatus = CategoryLookupStatus.SUCCESS)
            session.categorySelection.update(DestinationState(current = destination, routing = true))
            session.categorySelection.select(CategoryChoice.OTHER)
            session.categorySelection.confirm()
            val lateClassification = destination.copy(category = DestinationCategory.HOSPITAL)
            assertFalse(session.save(1, lateClassification))
            repository.shouldFail = false
            assertTrue(session.save(1, lateClassification))
            val attempted = repository.attempts.first()
            val saved = repository.saved.single().second
            assertEquals(attempted.stops, saved.stops)
            assertNull(saved.stops.single().category)
        }

    @Test
    fun arrivalCoordinatesAreMappedOnlyWhenTheTripIsSaved() =
        runBlocking {
            val region =
                AdministrativeBoundary(
                    "11140",
                    "중구",
                    listOf(
                        BoundaryPolygon(
                            listOf(
                                listOf(
                                    GeoPoint(126.9, 37.5),
                                    GeoPoint(127.1, 37.5),
                                    GeoPoint(127.1, 37.6),
                                    GeoPoint(126.9, 37.6),
                                ),
                            ),
                        ),
                    ),
                )
            val resolver =
                ResolveDistricts(
                    GetAdministrativeBoundaries(
                        object : BoundaryRepository {
                            override suspend fun getBoundaries() = AdministrativeBoundaries(emptyList(), listOf(region))
                        },
                    ),
                )
            val repository = FakeRecords()
            val session =
                TripSessionViewModel(
                    repository,
                    FakeDestinationSource(),
                    ResolveDestinationCategory(),
                    resolver,
                    RecordingTripSessions(),
                )
            session.recordArrival(Destination(1, "시청", 37.55, 127.0, DestinationCategory.TOURISM))
            session.recordArrival(Destination(2, "중구 병원", 37.56, 127.0, DestinationCategory.HOSPITAL))
            session.recordArrival(Destination(3, "좌표 없음", 0.0, 0.0, DestinationCategory.MART))
            assertTrue(repository.saved.isEmpty())
            assertTrue(session.save(7, null))
            val stops =
                repository.saved
                    .single()
                    .second.stops
            assertEquals(listOf("11140", "11140", null), stops.map { it.districtCode })
            assertEquals(listOf(37.55, 37.56, null), stops.map { it.latitude })
        }

    @Test
    fun invalidCoordinatesAreExcludedButAValidPendingLocationSurvivesSaveRetry() =
        runBlocking {
            val repository = FakeRecords().apply { shouldFail = true }
            val session =
                TripSessionViewModel(
                    repository,
                    FakeDestinationSource(),
                    ResolveDestinationCategory(),
                    emptyDistricts(),
                    RecordingTripSessions(),
                )
            session.recordArrival(Destination(1, "위치 없음", 0.0, 0.0))
            session.recordArrival(Destination(2, "범위 밖", 91.0, 127.0))
            assertFalse(session.save(7, Destination(3, "재시도", 37.55, 127.0)))
            repository.shouldFail = false
            assertTrue(session.save(7, Destination(3, "재시도", 0.0, 0.0)))
            val stops =
                repository.saved
                    .single()
                    .second.stops
            assertEquals(listOf(null, null, 37.55), stops.map { it.latitude })
            assertEquals(listOf(null, null, 127.0), stops.map { it.longitude })
        }

    @Test
    fun failedBoundaryLookupDoesNotLoseTheTripRecord() =
        runBlocking {
            val resolver =
                ResolveDistricts(
                    GetAdministrativeBoundaries(
                        object : BoundaryRepository {
                            override suspend fun getBoundaries(): AdministrativeBoundaries =
                                error("boundary unavailable")
                        },
                    ),
                )
            val repository = FakeRecords()
            val session =
                TripSessionViewModel(
                    repository,
                    FakeDestinationSource(),
                    ResolveDestinationCategory(),
                    resolver,
                    RecordingTripSessions(),
                )
            session.recordArrival(Destination(1, "시청", 37.55, 127.0))
            assertTrue(session.save(7, null))
            assertEquals(
                "시청",
                repository.saved
                    .single()
                    .second.stops
                    .single()
                    .placeName,
            )
            assertNull(
                repository.saved
                    .single()
                    .second.stops
                    .single()
                    .districtCode,
            )
        }

    @Test
    fun destinationSourceRemainsObservedUntilTheHomeSessionScopeEnds() {
        val source = FakeDestinationSource()
        val session =
            TripSessionViewModel(
                FakeRecords(),
                source,
                ResolveDestinationCategory(),
                emptyDistricts(),
                RecordingTripSessions(),
            )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            session.startDestinationObservation(scope)
            session.startDestinationObservation(scope)
            assertEquals(1, source.subscriptions)
            source.session.routeStarted()
            source.session.destinationChanged("시장", 37.0, 127.0)
            val active = session.destination.value.current
            assertEquals("시장", active?.name)
            source.session.arrive("시장")
            val pending = session.destination.value.pendingArrival
            assertEquals("시장", pending?.name)
            source.session.acknowledgeArrival(pending!!.id)
            assertNull(session.destination.value.pendingArrival)
            source.session.routeStarted()
            source.session.destinationChanged("병원", 37.1, 127.1)
            source.session.clearCurrent()
            assertNull(session.destination.value.current)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun propsRequireConfirmedRouteAndLateLookupSurvivesArrivalAcknowledgement() =
        runBlocking {
            val source = FakeDestinationSource()
            val props = RecordingTripSessions()
            val vm = TripSessionViewModel(FakeRecords(), source, ResolveDestinationCategory(), emptyDistricts(), props)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            try {
                vm.startDestinationObservation(scope, 7, "trip-1")
                source.session.destinationChanged("음식점", 37.0, 127.0)
                val first = source.session.state.current!!
                source.session.categoryResolved(
                    first.id,
                    CategoryLookupResult(DestinationCategory.FOOD, CategoryLookupStatus.SUCCESS),
                )
                assertTrue(props.acquisitions.isEmpty())
                source.session.routeStarted()
                assertEquals(listOf(DestinationCategory.FOOD), props.acquisitions.map { it.third })
                source.session.arrive("음식점")
                source.session.acknowledgeArrival(first.id)
                source.session.routeStarted()
                source.session.destinationChanged("관광지", 37.1, 127.1)
                val second = source.session.state.current!!
                source.session.arrive("관광지")
                source.session.acknowledgeArrival(second.id)
                assertTrue(source.session.lookupDestinations().any { it.id == second.id })
                source.session.categoryResolved(
                    second.id,
                    CategoryLookupResult(DestinationCategory.TOURISM, CategoryLookupStatus.SUCCESS),
                )
                assertEquals(
                    listOf(DestinationCategory.FOOD, DestinationCategory.TOURISM),
                    props.acquisitions.map { it.third },
                )
                source.session.clearCurrent()
                assertEquals(2, props.acquisitions.size)
                assertTrue(props.acquisitions.all { it.first == 7L && it.second == "trip-1" })
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun loadingFailuresAndOtherDoNotAwardPropsButManualConfirmationDoes() =
        runBlocking {
            val props = RecordingTripSessions()
            val vm =
                TripSessionViewModel(
                    FakeRecords(),
                    FakeDestinationSource(),
                    ResolveDestinationCategory(),
                    emptyDistricts(),
                    props,
                )
            val destination = place(10, "학교", DestinationCategory.SCHOOL)
            for (status in listOf(
                CategoryLookupStatus.IDLE,
                CategoryLookupStatus.LOADING,
                CategoryLookupStatus.FAILURE,
            )) {
                vm.acquireDestinationProp(7, "trip", destination.copy(categoryStatus = status))
            }
            assertTrue(props.acquisitions.isEmpty())
            val failed = destination.copy(category = null, categoryStatus = CategoryLookupStatus.FAILURE)
            vm.categorySelection.update(DestinationState(current = failed, routing = true))
            vm.categorySelection.select(CategoryChoice.SCHOOL)
            vm.categorySelection.confirm()
            vm.acquireDestinationProp(7, "trip", failed)
            vm.acquireDestinationProp(
                7,
                "trip",
                failed.copy(category = DestinationCategory.MART, categoryStatus = CategoryLookupStatus.SUCCESS),
            )
            assertEquals(listOf(DestinationCategory.SCHOOL), props.acquisitions.map { it.third })
            val other = failed.copy(id = 11)
            vm.categorySelection.update(DestinationState(current = other, routing = true))
            vm.categorySelection.dismiss()
            vm.acquireDestinationProp(
                7,
                "trip",
                other.copy(category = DestinationCategory.MART, categoryStatus = CategoryLookupStatus.SUCCESS),
            )
            assertEquals(1, props.acquisitions.size)
        }

    @Test
    fun restoredConfirmedDestinationBackfillsAnExistingTrip() {
        val source = FakeDestinationSource()
        source.session.destinationChanged("병원", 37.0, 127.0)
        source.session.routeStarted()
        source.session.categoryResolved(
            source.session.state.current!!
                .id,
            CategoryLookupResult(DestinationCategory.HOSPITAL, CategoryLookupStatus.SUCCESS),
        )
        val props = RecordingTripSessions()
        val vm = TripSessionViewModel(FakeRecords(), source, ResolveDestinationCategory(), emptyDistricts(), props)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            vm.startDestinationObservation(scope, 7, "existing-session")
            assertEquals(listOf(Triple(7L, "existing-session", DestinationCategory.HOSPITAL)), props.acquisitions)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun manualConfirmationUsesTripScopeInsteadOfTheDepartingScreenScope() =
        runBlocking {
            val gate = CompletableDeferred<Unit>()
            val props = RecordingTripSessions().apply { beforeAcquire = { gate.await() } }
            val vm =
                TripSessionViewModel(
                    FakeRecords(),
                    FakeDestinationSource(),
                    ResolveDestinationCategory(),
                    emptyDistricts(),
                    props,
                )
            val tripScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            try {
                val destination =
                    place(
                        10,
                        "학교",
                        DestinationCategory.SCHOOL,
                    ).copy(categoryStatus = CategoryLookupStatus.SUCCESS)
                val navigation =
                    screenScope.async {
                        vm.confirmDestination(7, "trip", destination, tripScope)
                    }
                navigation.await()
                screenScope.cancel()
                assertTrue(props.acquisitions.isEmpty())
                gate.complete(Unit)
                assertEquals(listOf(Triple(7L, "trip", DestinationCategory.SCHOOL)), props.acquisitions)
            } finally {
                screenScope.cancel()
                tripScope.cancel()
            }
        }

    private fun place(
        id: Long,
        name: String,
        category: DestinationCategory? = null,
    ) = Destination(id, name, 37.0, 127.0, category)

    private class FakeDestinationSource : DestinationSource {
        val session = DestinationSession()
        val acknowledged = mutableListOf<Long>()
        var subscriptions = 0

        override fun observe() =
            flow {
                subscriptions++
                emitAll(session.states)
            }

        override fun pendingArrivals() = session.pendingArrivals()

        override fun acknowledgeArrival(id: Long) {
            acknowledged.add(id)
            session.acknowledgeArrival(id)
        }

        override fun createDemoArrival(destination: Destination) = session.createDemoArrival(destination)
    }

    private class FakeRecords : RecordRepository {
        val saved = mutableListOf<Pair<Long, NewTripRecord>>()
        val attempts = mutableListOf<NewTripRecord>()
        var shouldFail = false
        var beforeSave: suspend () -> Unit = {}

        override fun observeRecords(profileId: Long): Flow<List<TripRecord>> = flowOf(emptyList())

        override fun observeRecord(
            profileId: Long,
            recordId: Long,
        ): Flow<TripRecordDetail?> = flowOf(null)

        override suspend fun saveCompletedRecord(
            profileId: Long,
            record: NewTripRecord,
        ): Long {
            attempts.add(record)
            beforeSave()
            if (shouldFail) error("storage unavailable")
            saved += profileId to record
            return saved.size.toLong()
        }
    }
}
