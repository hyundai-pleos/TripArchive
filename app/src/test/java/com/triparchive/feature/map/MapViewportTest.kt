package com.triparchive.feature.map

import com.triparchive.presentation.map.MapViewport
import com.triparchive.presentation.map.VisitMapAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapViewportTest {
    @Test
    fun `districts appear from the third zoom level`() {
        var viewport = MapViewport()

        for (expected in listOf(false, false, true, true, true)) {
            assertEquals(expected, viewport.showsDistricts)
            viewport = viewport.reduce(VisitMapAction.ZoomIn)
        }
        for (expected in listOf(true, true, false, false)) {
            viewport = viewport.reduce(VisitMapAction.ZoomOut)
            assertEquals(expected, viewport.showsDistricts)
        }
    }

    @Test
    fun `gesture zoom uses the same district threshold`() {
        assertFalse(MapViewport(zoom = 3.159f).showsDistricts)
        assertTrue(MapViewport(zoom = 3.16f).showsDistricts)
    }

    @Test
    fun `buttons change zoom by the same ratio after a gesture`() {
        for (zoom in listOf(1f, 1.5f, 2.2f)) {
            val viewport = MapViewport(zoom = zoom)

            assertEquals(1.77828f, viewport.reduce(VisitMapAction.ZoomIn).zoom / zoom, 0.001f)
            assertEquals(zoom, viewport.reduce(VisitMapAction.ZoomIn).reduce(VisitMapAction.ZoomOut).zoom, 0.001f)
        }
        for (zoom in listOf(2.25f, 3f, 5f)) {
            assertEquals(1.77828f, zoom / MapViewport(zoom = zoom).reduce(VisitMapAction.ZoomOut).zoom, 0.001f)
        }
    }

    @Test
    fun `zooming from full view has five levels including the maximum`() {
        var viewport = MapViewport()

        for (expectedZoom in listOf(1f, 1.77828f, 3.16228f, 5.62342f, 10f)) {
            assertEquals(expectedZoom, viewport.zoom, 0.001f)
            viewport = viewport.reduce(VisitMapAction.ZoomIn)
        }
        assertEquals(10f, viewport.zoom, 0.001f)
    }

    @Test
    fun `button zoom stops at minimum and maximum`() {
        assertEquals(1f, MapViewport(zoom = 1f).reduce(VisitMapAction.ZoomOut).zoom, 0.001f)
        assertEquals(10f, MapViewport(zoom = 10f).reduce(VisitMapAction.ZoomIn).zoom, 0.001f)
        assertEquals(1f, MapViewport(zoom = 1.1f).reduce(VisitMapAction.ZoomOut).zoom, 0.001f)
        assertEquals(10f, MapViewport(zoom = 9.9f).reduce(VisitMapAction.ZoomIn).zoom, 0.001f)
    }

    @Test
    fun `panning stays within the original viewport at each zoom`() {
        val gesture = VisitMapAction.Transform(1f, 1000f, -1000f, 100f, 50f, 200f, 100f)

        val atMinimum = MapViewport().reduce(gesture)
        assertEquals(0f, atMinimum.offsetX, 0.001f)
        assertEquals(0f, atMinimum.offsetY, 0.001f)

        val zoomed = MapViewport(zoom = 3f).reduce(gesture)
        assertEquals(200f, zoomed.offsetX, 0.001f)
        assertEquals(-100f, zoomed.offsetY, 0.001f)

        val zoomedOut = zoomed.reduce(VisitMapAction.ZoomOut)
        assertEquals(68.7023f, zoomedOut.offsetX, 0.001f)
        assertEquals(-34.3512f, zoomedOut.offsetY, 0.001f)
        val fullyZoomedOut = (1..5).fold(zoomedOut) { viewport, _ -> viewport.reduce(VisitMapAction.ZoomOut) }
        assertEquals(1f, fullyZoomedOut.zoom, 0.001f)
        assertEquals(0f, fullyZoomedOut.offsetX, 0.001f)
        assertEquals(0f, fullyZoomedOut.offsetY, 0.001f)
    }

    @Test
    fun `button zoom preserves the center after panning`() {
        val viewport =
            MapViewport(
                zoom = 2f,
                offsetX = 20f,
                offsetY = -10f,
                viewportWidth = 200f,
                viewportHeight = 100f
            )

        val zoomed = viewport.reduce(VisitMapAction.ZoomIn)

        assertEquals(35.5656f, zoomed.offsetX, 0.001f)
        assertEquals(-17.7828f, zoomed.offsetY, 0.001f)
        val restored = zoomed.reduce(VisitMapAction.ZoomOut)
        assertEquals(viewport.zoom, restored.zoom, 0.001f)
        assertEquals(viewport.offsetX, restored.offsetX, 0.001f)
        assertEquals(viewport.offsetY, restored.offsetY, 0.001f)
    }

    @Test
    fun `resizing clamps the position to the new viewport`() {
        val viewport =
            MapViewport(
                zoom = 3f,
                offsetX = 200f,
                offsetY = -100f,
                viewportWidth = 200f,
                viewportHeight = 100f
            )
                .reduce(VisitMapAction.ViewportResized(80f, 40f))

        assertEquals(80f, viewport.offsetX, 0.001f)
        assertEquals(-40f, viewport.offsetY, 0.001f)
    }
}
