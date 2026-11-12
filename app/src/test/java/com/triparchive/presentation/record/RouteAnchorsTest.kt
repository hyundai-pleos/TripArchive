package com.triparchive.presentation.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteAnchorsTest {
    @Test
    fun emptyAndSingleStopsHaveNoConnections() {
        assertTrue(routeAnchors(0).isEmpty())
        assertEquals(1, routeAnchors(1).size)
    }

    @Test
    fun twoAndThreeStopsUseDesignPositions() {
        assertEquals(2, routeAnchors(2).size)
        val points = routeAnchors(3)
        assertEquals(3, points.size)
        assertTrue(points[0].x < points[1].x)
        assertTrue(points[2].y < points[1].y)
    }

    @Test
    fun manyStopsHaveDistinctInBoundsPositions() {
        for (count in 4..30) {
            val points = routeAnchors(count)
            assertEquals(count, points.size)
            assertEquals(count, points.distinct().size)
            assertTrue(points.all { it.x in 0f..1f && it.y in 0f..1f })
        }
    }
}
