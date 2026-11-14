package com.triparchive.presentation.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRequestLifetimeTest {
    @Test
    fun closingPendingRequestCancelsBeforeDestroyingItsAsset() {
        val events = mutableListOf<String>()
        val lifetime = lifetime(events)

        lifetime.beginLoading()
        assertFalse(lifetime.canPublish)
        lifetime.close()
        lifetime.close()

        assertEquals(listOf("cancel", "evict", "destroyAsset", "destroyLoader"), events)
        assertFalse(lifetime.canPublish)
        assertFalse(lifetime.completeLoading())
    }

    @Test
    fun completedAssetStaysPublishableUntilClosedAndIsDestroyedOnce() {
        val events = mutableListOf<String>()
        val lifetime = lifetime(events)

        lifetime.beginLoading()
        assertTrue(lifetime.completeLoading())
        assertFalse(lifetime.completeLoading())
        assertTrue(lifetime.canPublish)
        assertEquals(listOf("evict", "releaseSource", "destroyLoader"), events)

        lifetime.close()
        lifetime.close()

        assertEquals(listOf("evict", "releaseSource", "destroyLoader", "destroyAsset"), events)
        assertFalse(lifetime.canPublish)
    }

    @Test
    fun closingBeforeLoadDoesNotCancelOrEvict() {
        val events = mutableListOf<String>()
        val lifetime = lifetime(events)

        lifetime.close()

        assertEquals(listOf("destroyAsset", "destroyLoader"), events)
        assertFalse(lifetime.canPublish)
    }

    private fun lifetime(events: MutableList<String>) =
        ModelRequestLifetime(
            cancelLoad = { events += "cancel" },
            evictResourceData = { events += "evict" },
            releaseSourceData = { events += "releaseSource" },
            destroyAsset = { events += "destroyAsset" },
            destroyResourceLoader = { events += "destroyLoader" },
        )
}
