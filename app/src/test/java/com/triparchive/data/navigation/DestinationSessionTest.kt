package com.triparchive.data.navigation

import com.triparchive.domain.navigation.CategoryLookupResult
import com.triparchive.domain.navigation.CategoryLookupStatus
import com.triparchive.domain.navigation.DestinationCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationSessionTest {
    private fun result(category: DestinationCategory) = CategoryLookupResult(category, CategoryLookupStatus.SUCCESS)

    @Test
    fun replacingActiveRouteKeepsNewDestinationWhenOldLookupCompletes() {
        val session = DestinationSession()
        session.routeStarted()
        session.destinationChanged("목적지 A", 37.0, 127.0)
        val first = session.state.current!!
        session.clearCurrent()
        session.routeStarted()
        session.destinationChanged("목적지 B", 37.1, 127.1)
        val second = session.state.current!!
        session.categoryResolved(second.id, result(DestinationCategory.HOSPITAL))
        session.categoryResolved(first.id, result(DestinationCategory.MART))

        assertTrue(session.state.routing)
        assertTrue(first.id != second.id)
        assertEquals("목적지 B", session.state.current!!.name)
        assertEquals(DestinationCategory.HOSPITAL, session.state.current!!.category)
        assertNull(session.state.pendingArrival)
    }

    @Test
    fun sdkPollingKeepsIdAndCoordinatesDistinguishPlacesWithSameName() {
        val session = DestinationSession()
        session.destinationChanged("시장", 37.0, 127.0)
        val first = session.state.current!!
        session.destinationChanged("시장", 37.0, 127.0)
        assertEquals(first.id, session.state.current!!.id)
        session.destinationChanged("시장", 38.0, 128.0)
        assertTrue(session.state.current!!.id > first.id)
    }

    @Test
    fun poiIdentityDistinguishesPlacesAndKeepsVisitAcrossChangedDisplayInformation() {
        val session = DestinationSession()
        session.destinationChanged("시장", 37.0, 127.0, poiId = "1", poiProvider = "provider", poiSubId = "a")
        val first = session.state.current!!
        session.destinationChanged("시장 이름 변경", 37.01, 127.01, poiId = "1", poiProvider = "provider", poiSubId = "a")
        assertEquals(first.id, session.state.current!!.id)
        session.destinationChanged("시장", 37.0, 127.0, poiId = "2", poiProvider = "provider", poiSubId = "a")
        assertTrue(session.state.current!!.id > first.id)
        val second = session.state.current!!
        session.destinationChanged("시장", 37.0, 127.0, poiId = "2", poiProvider = "provider", poiSubId = "b")
        assertTrue(session.state.current!!.id > second.id)
    }

    @Test
    fun poiIdentityCanBeAddedOrTemporarilyOmittedWithoutReplacingVisit() {
        val session = DestinationSession()
        session.destinationChanged("시장", 37.0, 127.0)
        val first = session.state.current!!
        session.destinationChanged("시장", 37.0, 127.0, poiId = "1", poiProvider = "provider")
        assertEquals(first.id, session.state.current!!.id)
        session.destinationChanged("시장", 37.0, 127.0)
        assertEquals(first.id, session.state.current!!.id)
        session.destinationChanged("시장", 37.0, 127.0, poiId = "2", poiProvider = "provider")
        assertTrue(session.state.current!!.id > first.id)
    }

    @Test
    fun completedPoiIgnoresPollingUntilNewRouteButNotAnotherPoiAtSameCoordinates() {
        val session = DestinationSession()
        session.destinationChanged("시장", 37.0, 127.0, poiId = "1", poiProvider = "provider")
        val first = session.state.current!!
        session.arrive("시장")
        session.destinationChanged("시장", 37.0, 127.0, poiId = "1", poiProvider = "provider")
        assertNull(session.state.current)
        session.destinationChanged("시장", 37.0, 127.0, poiId = "2", poiProvider = "provider")
        assertTrue(session.state.current!!.id > first.id)
    }

    @Test
    fun nextDestinationDoesNotReplacePendingArrivalOrItsCategory() {
        val session = DestinationSession()
        session.destinationChanged("시장", 37.0, 127.0)
        val market = session.state.current!!
        session.categoryResolved(market.id, result(DestinationCategory.MART))
        session.arrive("시장")
        session.routeStarted()
        session.destinationChanged("병원", 37.1, 127.1)
        val hospital = session.state.current!!
        session.categoryResolved(hospital.id, result(DestinationCategory.HOSPITAL))
        assertEquals(market.id, session.state.pendingArrival!!.id)
        assertEquals("시장", session.state.pendingArrival!!.name)
        assertEquals(DestinationCategory.MART, session.state.pendingArrival!!.category)
        assertEquals(DestinationCategory.HOSPITAL, session.state.current!!.category)
    }

    @Test
    fun lateLookupUpdatesPendingArrivalAfterNewDestinationIsSet() {
        val session = DestinationSession()
        session.destinationChanged("시장", 37.0, 127.0)
        val market = session.state.current!!
        session.arrive("시장")
        session.routeStarted()
        session.destinationChanged("병원", 37.1, 127.1)
        val hospital = session.state.current!!
        assertEquals(setOf(market.id, hospital.id), session.lookupDestinations().map { it.id }.toSet())
        session.categoryResolved(market.id, result(DestinationCategory.MART))
        assertEquals(DestinationCategory.MART, session.state.pendingArrival!!.category)
        assertNull(session.state.current!!.category)
        assertEquals(CategoryLookupStatus.LOADING, session.state.current!!.categoryStatus)
    }

    @Test
    fun emptyDestinationAndRouteCancellationPreservePendingArrival() {
        val session = DestinationSession()
        session.destinationChanged("시장", 37.0, 127.0)
        val market = session.state.current!!
        session.categoryResolved(market.id, result(DestinationCategory.MART))
        session.arrive("시장")
        session.destinationChanged("", 0.0, 0.0)
        session.clearCurrent()
        assertEquals(market.id, session.state.pendingArrival!!.id)
        assertEquals(DestinationCategory.MART, session.state.pendingArrival!!.category)
    }

    @Test
    fun revisitingSamePlaceCreatesNewIdAndOldResultsCannotUpdateIt() {
        val session = DestinationSession()
        session.destinationChanged("시장", 37.0, 127.0)
        val first = session.state.current!!
        session.arrive("시장")
        session.acknowledgeArrival(first.id)
        session.destinationChanged("시장", 37.0, 127.0)
        assertNull(session.state.current)
        session.routeStarted()
        session.destinationChanged("시장", 37.0, 127.0)
        val second = session.state.current!!
        assertTrue(second.id > first.id)
        session.categoryResolved(first.id, result(DestinationCategory.FOOD))
        assertNull(session.state.current!!.category)
        assertNull(session.state.pendingArrival)
    }

    @Test
    fun reselectingSamePlaceBeforeGuidanceStartsCreatesNewVisit() {
        val session = DestinationSession()
        session.destinationChanged("시장", 37.0, 127.0)
        session.arrive("시장")
        val first = session.state.pendingArrival!!
        session.acknowledgeArrival(first.id)
        session.destinationSelectionChanged()
        session.destinationChanged("시장", 37.0, 127.0)
        assertTrue(session.state.current!!.id > first.id)
    }

    @Test
    fun clearingRouteDoesNotResetIdSequenceAndLateResultsAreIgnored() {
        val session = DestinationSession()
        session.destinationChanged("시장", 37.0, 127.0)
        val first = session.state.current!!
        session.clearCurrent()
        session.destinationChanged("시장", 37.0, 127.0)
        assertTrue(session.state.current!!.id > first.id)
        session.categoryResolved(first.id, result(DestinationCategory.FOOD))
        assertNull(session.state.current!!.category)
    }

    @Test
    fun acknowledgmentIsByIdAndLateResultCannotRestoreAcknowledgedArrival() {
        val session = DestinationSession()
        session.destinationChanged("시장", 37.0, 127.0)
        val market = session.state.current!!
        session.arrive("시장")
        session.acknowledgeArrival(market.id + 1)
        assertNotNull(session.state.pendingArrival)
        session.acknowledgeArrival(market.id)
        session.categoryResolved(market.id, result(DestinationCategory.MART))
        assertNull(session.state.pendingArrival)
        assertTrue(session.lookupDestinations().isEmpty())
    }

    @Test
    fun consecutiveArrivalsAwaitConfirmationInOrder() {
        val session = DestinationSession()
        session.destinationChanged("시장", 37.0, 127.0)
        session.arrive("시장")
        val market = session.state.pendingArrival!!
        session.routeStarted()
        session.destinationChanged("병원", 37.1, 127.1)
        val hospital = session.state.current!!
        session.arrive("병원")
        session.categoryResolved(hospital.id, result(DestinationCategory.HOSPITAL))
        assertEquals(market.id, session.state.pendingArrival!!.id)
        session.acknowledgeArrival(market.id)
        assertEquals(hospital.id, session.state.pendingArrival!!.id)
        assertEquals(DestinationCategory.HOSPITAL, session.state.pendingArrival!!.category)
    }

    @Test
    fun demoVisitsHaveUniqueIdsWithoutChangingCurrentRoute() {
        val session = DestinationSession()
        session.destinationChanged("시장", 37.0, 127.0)
        val current = session.state.current!!
        val first = session.createDemoArrival(current)
        val second = session.createDemoArrival(current)
        assertTrue(first.id > current.id)
        assertTrue(second.id > first.id)
        assertEquals(current, session.state.current)
        assertNull(session.state.pendingArrival)
    }
}
