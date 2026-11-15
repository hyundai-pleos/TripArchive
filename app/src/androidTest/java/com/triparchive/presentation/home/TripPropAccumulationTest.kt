package com.triparchive.presentation.home

import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.triparchive.core.designsystem.TripArchiveTheme
import com.triparchive.data.local.TripArchiveDatabase
import com.triparchive.data.navigation.DestinationSession
import com.triparchive.data.profile.Profile
import com.triparchive.data.profile.ProfileRelationship
import com.triparchive.data.record.RoomRecordRepository
import com.triparchive.data.trip.RoomTripSessionRepository
import com.triparchive.domain.map.AdministrativeBoundaries
import com.triparchive.domain.map.BoundaryRepository
import com.triparchive.domain.map.GetAdministrativeBoundaries
import com.triparchive.domain.map.ResolveDistricts
import com.triparchive.domain.navigation.CategoryLookupResult
import com.triparchive.domain.navigation.CategoryLookupStatus
import com.triparchive.domain.navigation.Destination
import com.triparchive.domain.navigation.DestinationCategory
import com.triparchive.domain.navigation.DestinationSource
import com.triparchive.domain.navigation.ResolveDestinationCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class TripPropAccumulationTest {
    @Test
    fun confirmedVisitsAccumulateThroughRepositoryAndRenderWithoutReplacingEarlierProps() =
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, "android.car.permission.CAR_SPEED")
            val database = Room.inMemoryDatabaseBuilder(context, TripArchiveDatabase::class.java).build()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val scenario = ActivityScenario.launch(ComponentActivity::class.java)
            try {
                val profile = Profile(nickname = "누적 검증", relationship = ProfileRelationship.CHILD)
                val id = database.profileDao().insert(profile)
                val sessions = RoomTripSessionRepository(database, database.tripSessionDao())
                val first = sessions.openOrResume(profile.copy(id = id))
                val source = TestDestinationSource()
                val vm =
                    TripSessionViewModel(
                        RoomRecordRepository(database.recordDao()),
                        source,
                        ResolveDestinationCategory(),
                        ResolveDistricts(
                            GetAdministrativeBoundaries(
                                object : BoundaryRepository {
                                    override suspend fun getBoundaries() =
                                        AdministrativeBoundaries(emptyList(), emptyList())
                                },
                            ),
                        ),
                        sessions,
                    )
                instrumentation.runOnMainSync { vm.startDestinationObservation(scope, id, first.sessionId) }
                scenario.onActivity { activity ->
                    activity.setContent {
                        val trip by sessions.observe(id).collectAsState(initial = first)
                        TripArchiveTheme {
                            DestinationHome(
                                profile = profile.copy(id = id),
                                onProfileClick = {},
                                onFinish = {},
                                source = vm.destination,
                                tripSessionId = first.sessionId,
                                acquiredProps = trip?.props.orEmpty(),
                                categorySelectionState = vm.categorySelection,
                                onArrival = vm::recordArrival,
                                onDestinationConfirmed = { vm.confirmDestination(id, first.sessionId, it, scope) },
                            )
                        }
                    }
                }
                val sequence =
                    listOf(
                        DestinationCategory.FOOD,
                        DestinationCategory.TOURISM,
                        DestinationCategory.FOOD,
                        DestinationCategory.HOSPITAL,
                        DestinationCategory.SCHOOL,
                        DestinationCategory.MART,
                    )
                val acquired = linkedSetOf<DestinationCategory>()
                sequence.forEachIndexed { index, category ->
                    instrumentation.runOnMainSync {
                        source.session.routeStarted()
                        source.session.destinationChanged("검증 목적지 ${index + 1}", 37.0 + index * 0.01, 127.0)
                        source.session.categoryResolved(
                            source.session.state.current!!
                                .id,
                            CategoryLookupResult(category, CategoryLookupStatus.SUCCESS),
                        )
                    }
                    acquired += category
                    val observed =
                        withTimeout(
                            10000,
                        ) { sessions.observe(id).first { it?.props == acquired.toList() } }!!
                    assertEquals(first.sessionId, observed.sessionId)
                    assertEquals(first.lookId, observed.lookId)
                    Thread.sleep(if (index == 0) 10000 else 2000)
                    ParcelFileDescriptor
                        .AutoCloseInputStream(
                            instrumentation.uiAutomation.executeShellCommand(
                                "screencap -p /data/local/tmp/home-29-${acquired.size}.png",
                            ),
                        ).use { it.readBytes() }
                    instrumentation.runOnMainSync {
                        source.session.arrive("검증 목적지 ${index + 1}")
                        vm.recordArrival(source.session.state.pendingArrival!!)
                    }
                    assertEquals(acquired.toList(), sessions.observe(id).first()!!.props)
                }
                assertEquals(
                    5,
                    sessions
                        .observe(id)
                        .first()!!
                        .props.size,
                )
            } finally {
                scenario.close()
                scope.cancel()
                database.close()
            }
        }

    private class TestDestinationSource : DestinationSource {
        val session = DestinationSession()

        override fun observe() = session.states

        override fun pendingArrivals() = session.pendingArrivals()

        override fun acknowledgeArrival(id: Long) = session.acknowledgeArrival(id)

        override fun createDemoArrival(destination: Destination) = session.createDemoArrival(destination)
    }
}
