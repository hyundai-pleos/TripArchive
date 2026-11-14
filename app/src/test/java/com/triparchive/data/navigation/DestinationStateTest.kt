package com.triparchive.data.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationStateTest {
    @Test
    fun destinationCategoryIsCapturedAtArrivalAndClearedOnAcknowledge() {
        val routing =
            DestinationState()
                .routeChanged(true)
                .destinationChanged("이마트 강릉점", DestinationCategory.MART)

        val arrived = routing.arrive("이마트 강릉점")

        assertEquals("이마트 강릉점", arrived.arrived)
        assertNull(arrived.name)
        assertNull(arrived.category)
        assertEquals(DestinationCategory.MART, arrived.arrivedCategory)
        assertNull(arrived.acknowledge().arrived)
        assertNull(arrived.acknowledge().arrivedCategory)
    }

    @Test
    fun cancelClearsActiveNameAndCategory() {
        val routing =
            DestinationState()
                .routeChanged(true)
                .destinationChanged("학교", DestinationCategory.SCHOOL)

        val cancelled = routing.routeChanged(false)

        assertNull(cancelled.name)
        assertNull(cancelled.category)
        assertEquals(false, cancelled.routing)
        assertNull(cancelled.destinationId)
        assertTrue(cancelled.cancelled)
    }

    @Test
    fun replacementDoesNotReusePreviousCategory() {
        val replaced =
            DestinationState()
                .destinationChanged("시장", DestinationCategory.MART)
                .destinationChanged("장소 이름만 있는 목적지")

        assertEquals("장소 이름만 있는 목적지", replaced.name)
        assertNull(replaced.category)
    }

    @Test
    fun arrivalOnlyKeepsCategoryWhenNameMatchesCurrentDestination() {
        val routing = DestinationState(name = "학교", category = DestinationCategory.SCHOOL)

        assertNull(routing.arrive("병원").arrivedCategory)
        assertEquals(DestinationCategory.SCHOOL, routing.arrive("학교").arrivedCategory)
    }

    @Test
    fun blankNameCannotHaveCategory() {
        val state = DestinationState().destinationChanged(" ", DestinationCategory.RESTAURANT)

        assertNull(state.name)
        assertNull(state.category)
        assertNull(state.destinationId)
    }

    @Test
    fun identityUsesPoiAndSeparatesDifferentProvidersAndSubIds() {
        val first = destinationIdentity("마트", "123", "1", "provider", 37.0, 127.0)
        assertEquals(first, destinationIdentity("이름 변경", "123", "1", "provider", 38.0, 128.0))
        assertNotEquals(first, destinationIdentity("마트", "123", "2", "provider", 37.0, 127.0))
        assertNotEquals(first, destinationIdentity("마트", "123", "1", "other", 37.0, 127.0))
    }

    @Test
    fun identityFallsBackToCoordinatesAndThenName() {
        val first = destinationIdentity("시장", latitude = 37.0, longitude = 127.0)
        assertEquals(first, destinationIdentity("시장", latitude = 37.0, longitude = 127.0))
        assertNotEquals(first, destinationIdentity("시장", latitude = 38.0, longitude = 127.0))
        assertEquals(destinationIdentity("시장"), destinationIdentity("시장", latitude = 0.0, longitude = 0.0))
        assertEquals(destinationIdentity("시장"), destinationIdentity("시장", latitude = Double.NaN, longitude = 127.0))
    }

    @Test
    fun arrivalCapturesIdentityOnlyForMatchingNameAndAcknowledgementClearsIt() {
        val active = DestinationState().routeChanged(true).destinationChanged("학교", destinationId = "poi:1")
        val arrived = active.arrive("학교")
        assertEquals("poi:1", arrived.arrivedDestinationId)
        assertNull(arrived.destinationId)
        assertNull(arrived.acknowledge().arrivedDestinationId)
        assertNull(active.arrive("병원").arrivedDestinationId)
    }

    @Test
    fun newRouteResetsCancellation() {
        val cancelled = DestinationState().routeChanged(true).destinationChanged("시장").routeChanged(false)
        val next = cancelled.routeChanged(true).destinationChanged("시장")
        assertEquals(false, next.cancelled)
        assertEquals(destinationIdentity("시장"), next.destinationId)
    }

    @Test
    fun lateGuidanceCannotResurrectCancelledRoute() {
        val cancelled = DestinationState().routeChanged(true).destinationChanged("시장").routeChanged(false)
        assertEquals(cancelled, cancelled.guidanceObserved())
        val next = cancelled.routingCompleted().guidanceObserved().destinationChanged("병원")
        assertEquals(true, next.routing)
        assertEquals(false, next.cancelled)
        assertEquals("병원", next.name)
    }

    @Test
    fun completedNewRouteClearsPreviousArrival() {
        val arrived = DestinationState().routeChanged(true).destinationChanged("시장").arrive("시장")
        val next = arrived.routingCompleted().guidanceObserved().destinationChanged("학교")
        assertNull(next.arrived)
        assertNull(next.arrivedCategory)
        assertNull(next.arrivedDestinationId)
        assertEquals("학교", next.name)
    }
}
