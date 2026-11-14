package com.triparchive.data.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    }
}
