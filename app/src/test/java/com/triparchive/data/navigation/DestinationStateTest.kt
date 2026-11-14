package com.triparchive.data.navigation

import com.triparchive.domain.navigation.CategoryLookupResult
import com.triparchive.domain.navigation.CategoryLookupStatus
import com.triparchive.domain.navigation.DestinationCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DestinationStateTest {
    private fun session(
        name: String,
        category: DestinationCategory,
    ): DestinationSession =
        DestinationSession().also {
            it.destinationChanged(name, 37.0, 127.0)
            it.categoryResolved(it.state.current!!.id, CategoryLookupResult(category, CategoryLookupStatus.SUCCESS))
            it.routeChanged(true)
        }

    @Test
    fun destinationCategoryIsCapturedAtArrivalAndClearedOnAcknowledge() {
        val session = session("이마트 강릉점", DestinationCategory.MART)
        session.arrive("이마트 강릉점")
        val arrived = session.state.pendingArrival!!
        assertEquals("이마트 강릉점", arrived.name)
        assertNull(session.state.current)
        assertEquals(DestinationCategory.MART, arrived.category)
        session.acknowledgeArrival(arrived.id)
        assertNull(session.state.pendingArrival)
    }

    @Test
    fun cancelClearsActiveNameAndCategory() {
        val session = session("학교", DestinationCategory.SCHOOL)
        session.clearCurrent()
        assertNull(session.state.current)
        assertEquals(false, session.state.routing)
    }

    @Test
    fun replacementDoesNotReusePreviousCategory() {
        val session = session("시장", DestinationCategory.MART)
        session.destinationChanged("장소 이름만 있는 목적지", 37.1, 127.1)
        assertEquals("장소 이름만 있는 목적지", session.state.current!!.name)
        assertNull(session.state.current!!.category)
    }

    @Test
    fun arrivalOnlyKeepsCategoryWhenNameMatchesCurrentDestination() {
        val session = session("학교", DestinationCategory.SCHOOL)
        session.arrive("병원")
        assertNull(session.state.pendingArrival!!.category)
        session.acknowledgeArrival(session.state.pendingArrival!!.id)
        session.arrive("학교")
        assertEquals(DestinationCategory.SCHOOL, session.state.pendingArrival!!.category)
    }

    @Test
    fun blankNameCannotHaveCategory() {
        val session = session("음식점", DestinationCategory.FOOD)
        session.destinationChanged(" ", 37.0, 127.0)
        assertNull(session.state.current)
    }
}
