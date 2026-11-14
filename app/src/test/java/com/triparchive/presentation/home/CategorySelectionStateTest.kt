package com.triparchive.presentation.home

import com.triparchive.data.navigation.DestinationCategory
import com.triparchive.data.navigation.DestinationState
import com.triparchive.presentation.home.components.CategoryChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategorySelectionStateTest {
    @Test
    fun selectionSurvivesRefreshAndIsCapturedAtArrival() {
        val selection = CategorySelectionState()
        val active = DestinationState(routing = true).destinationChanged("같은 이름", destinationId = "poi:1")
        selection.update(active)
        selection.select(CategoryChoice.SCHOOL)
        selection.confirm()
        selection.update(active.copy(category = DestinationCategory.HOSPITAL))
        assertFalse(selection.pending)
        assertEquals(DestinationCategory.SCHOOL, selection.categoryFor(active))

        val arrival = active.arrive("같은 이름")
        selection.update(arrival)
        selection.update(arrival)
        assertEquals(DestinationCategory.SCHOOL, selection.arrivalCategoryFor(arrival, "같은 이름"))
    }

    @Test
    fun timeoutWithoutSelectionAndBackResolveOtherUntilRouteChanges() {
        val selection = CategorySelectionState()
        val first = DestinationState(routing = true).destinationChanged("장소", destinationId = "poi:1")
        selection.update(first)
        repeat(50) { selection.tick() }
        assertFalse(selection.pending)
        assertNull(selection.categoryFor(first))
        selection.update(first)
        assertFalse(selection.pending)

        val second = first.destinationChanged("장소", destinationId = "poi:2")
        selection.update(second)
        assertTrue(selection.pending)
        selection.select(CategoryChoice.MART)
        selection.dismiss()
        assertNull(selection.categoryFor(second))
        selection.update(second)
        assertFalse(selection.pending)
    }

    @Test
    fun providerAndCancellationEndPendingSelection() {
        val selection = CategorySelectionState()
        val active = DestinationState(routing = true).destinationChanged("장소")
        selection.update(active)
        selection.select(CategoryChoice.MART)
        selection.update(active.copy(category = DestinationCategory.HOSPITAL))
        assertFalse(selection.pending)
        assertEquals(
            DestinationCategory.HOSPITAL,
            selection.categoryFor(active.copy(category = DestinationCategory.HOSPITAL)),
        )
        selection.update(active)
        assertTrue(selection.pending)
        assertNull(selection.selected)
        assertEquals(5, selection.secondsRemaining)
        selection.update(active.routeChanged(false))
        selection.update(active)
        assertTrue(selection.pending)
        assertNull(selection.selected)
        assertEquals(5, selection.secondsRemaining)
    }

    @Test
    fun arrivalWithDifferentIdentityDoesNotUseTheActiveManualChoice() {
        val selection = CategorySelectionState()
        val active = DestinationState(routing = true).destinationChanged("장소", destinationId = "one")
        selection.update(active)
        selection.select(CategoryChoice.MART)
        selection.confirm()
        val otherArrival = active.arrive("장소").copy(arrivedDestinationId = "two")
        assertNull(selection.arrivalCategoryFor(otherArrival, "장소"))
        selection.update(otherArrival)
        assertNull(selection.arrivalCategoryFor(otherArrival, "장소"))
    }

    @Test
    fun pendingSelectionIsDiscardedOnArrival() {
        val selection = CategorySelectionState()
        val active = DestinationState(routing = true).destinationChanged("장소")
        selection.update(active)
        selection.select(CategoryChoice.MART)
        val arrival = active.arrive("장소")
        selection.update(arrival)
        assertFalse(selection.pending)
        assertNull(selection.arrivalCategoryFor(arrival, "장소"))
    }
}
