package com.triparchive.presentation.home

import com.triparchive.domain.navigation.CategoryLookupStatus
import com.triparchive.domain.navigation.Destination
import com.triparchive.domain.navigation.DestinationCategory
import com.triparchive.domain.navigation.DestinationState
import com.triparchive.presentation.home.components.CategoryChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategorySelectionStateTest {
    private fun destination(
        id: Long,
        status: CategoryLookupStatus = CategoryLookupStatus.SUCCESS,
        category: DestinationCategory? = null,
    ) = Destination(id, "장소", 37.0, 127.0, category, status)

    private fun route(destination: Destination) = DestinationState(current = destination, routing = true)

    @Test
    fun waitsForLookupBeforePromptingAndKeepsManualSelectionAtArrival() {
        val selection = CategorySelectionState()
        val active = destination(1)
        selection.update(route(active.copy(categoryStatus = CategoryLookupStatus.IDLE)))
        assertFalse(selection.pending)
        selection.update(route(active.copy(categoryStatus = CategoryLookupStatus.LOADING)))
        assertFalse(selection.pending)
        selection.update(route(active))
        assertTrue(selection.pending)
        selection.select(CategoryChoice.SCHOOL)
        selection.confirm()
        selection.update(route(active.copy(category = DestinationCategory.HOSPITAL)))
        assertFalse(selection.pending)
        assertEquals(DestinationCategory.SCHOOL, selection.apply(active).category)
        selection.update(DestinationState(pendingArrival = active))
        assertEquals(DestinationCategory.SCHOOL, selection.apply(active).category)
    }

    @Test
    fun timeoutAndBackKeepOtherOnlyForTheCurrentVisit() {
        val selection = CategorySelectionState()
        val first = destination(1)
        selection.update(route(first))
        assertFalse(selection.hasDecision(first.id))
        repeat(50) { selection.tick() }
        assertFalse(selection.pending)
        assertTrue(selection.hasDecision(first.id))
        assertNull(selection.apply(first).category)
        selection.update(route(first))
        assertFalse(selection.pending)

        val second = destination(2)
        selection.update(route(second))
        assertTrue(selection.pending)
        assertFalse(selection.hasDecision(second.id))
        selection.select(CategoryChoice.MART)
        selection.dismiss()
        assertTrue(selection.hasDecision(second.id))
        assertNull(selection.apply(second).category)
        selection.update(route(second))
        assertFalse(selection.pending)
    }

    @Test
    fun automaticClassificationCancelsPromptAndFailureCanPrompt() {
        val selection = CategorySelectionState()
        val active = destination(1, CategoryLookupStatus.FAILURE)
        selection.update(route(active))
        selection.select(CategoryChoice.MART)
        val classified =
            active.copy(
                category = DestinationCategory.HOSPITAL,
                categoryStatus = CategoryLookupStatus.SUCCESS,
            )
        selection.update(route(classified))
        assertFalse(selection.pending)
        assertEquals(DestinationCategory.HOSPITAL, selection.apply(classified).category)
        selection.update(route(active))
        assertTrue(selection.pending)
        assertNull(selection.selected)
        assertEquals(5, selection.secondsRemaining)
        selection.update(DestinationState())
        assertFalse(selection.pending)
    }

    @Test
    fun temporaryInactiveRoutePreservesThePendingSelectionAndTime() {
        val selection = CategorySelectionState()
        val active = destination(1)
        selection.update(route(active))
        selection.select(CategoryChoice.MART)
        repeat(20) { selection.tick() }
        selection.update(DestinationState(current = active, routing = false))
        selection.update(route(active))
        assertTrue(selection.pending)
        assertEquals(CategoryChoice.MART, selection.selected)
        assertEquals(3, selection.secondsRemaining)
    }

    @Test
    fun visitsWithSameNameDoNotShareManualSelection() {
        val selection = CategorySelectionState()
        val first = destination(1)
        val second = destination(2)
        selection.update(route(first))
        selection.select(CategoryChoice.MART)
        selection.confirm()
        selection.update(route(second))
        assertNull(selection.apply(second).category)
        assertTrue(selection.pending)
        assertEquals(DestinationCategory.MART, selection.apply(first).category)
    }

    @Test
    fun simulatedArrivalCanInheritManualChoice() {
        val selection = CategorySelectionState()
        val active = destination(1)
        val simulated = destination(2)
        selection.update(route(active))
        selection.select(CategoryChoice.TOURISM)
        selection.confirm()
        selection.copyDecision(active, simulated)
        assertEquals(DestinationCategory.TOURISM, selection.apply(simulated).category)
    }

    @Test
    fun arrivalBeforeConfirmationDoesNotSavePendingChoice() {
        val selection = CategorySelectionState()
        val active = destination(1)
        selection.update(route(active))
        selection.select(CategoryChoice.MART)
        selection.update(DestinationState(pendingArrival = active))
        assertFalse(selection.pending)
        assertNull(selection.apply(active).category)
    }
}
