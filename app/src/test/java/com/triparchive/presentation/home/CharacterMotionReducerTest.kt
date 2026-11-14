package com.triparchive.presentation.home

import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterMotionReducerTest {
    @Test
    fun movingBandChangesWaitForStableSpeedAndUseHysteresis() {
        val reducer = CharacterMotionReducer()

        assertEquals(CharacterMotionBand.UNAVAILABLE, reducer.update(5f, 0L))
        assertEquals(CharacterMotionBand.UNAVAILABLE, reducer.update(5f, 1_999L))
        assertEquals(CharacterMotionBand.LOW_SPEED, reducer.update(5f, 2_000L))

        assertEquals(CharacterMotionBand.LOW_SPEED, reducer.update(30f / 3.6f, 3_000L))
        assertEquals(CharacterMotionBand.LOW_SPEED, reducer.update(32f / 3.6f, 4_000L))
        assertEquals(CharacterMotionBand.LOW_SPEED, reducer.update(33f / 3.6f, 5_000L))
        assertEquals(CharacterMotionBand.GENERAL_DRIVING, reducer.update(33f / 3.6f, 7_000L))

        assertEquals(CharacterMotionBand.GENERAL_DRIVING, reducer.update(29f / 3.6f, 8_000L))
        assertEquals(CharacterMotionBand.GENERAL_DRIVING, reducer.update(26f / 3.6f, 9_000L))
        assertEquals(CharacterMotionBand.LOW_SPEED, reducer.update(26f / 3.6f, 11_000L))
    }

    @Test
    fun stoppedInDriveIsWaitingWithoutIdleAndLossOfSpeedFallsBackImmediately() {
        val reducer = CharacterMotionReducer(CharacterMotionBand.WAITING)

        assertEquals(CharacterMotionBand.WAITING, reducer.update(0f, 0L))
        assertEquals(false, CharacterMotionPresentation(reducer.band, drivingRestricted = false).idleEnabled)
        assertEquals(CharacterMotionBand.UNAVAILABLE, reducer.update(null, 1L))
    }

    @Test
    fun waitingBoundaryUsesHysteresisAndDwell() {
        val reducer = CharacterMotionReducer(CharacterMotionBand.WAITING)
        assertEquals(CharacterMotionBand.WAITING, reducer.update(3.1f / 3.6f, 0L))
        assertEquals(CharacterMotionBand.WAITING, reducer.update(5f / 3.6f, 3_000L))
        assertEquals(CharacterMotionBand.LOW_SPEED, reducer.update(5f / 3.6f, 5_000L))
        assertEquals(CharacterMotionBand.LOW_SPEED, reducer.update(2.9f / 3.6f, 6_000L))
        assertEquals(CharacterMotionBand.LOW_SPEED, reducer.update(0f, 7_000L))
        assertEquals(CharacterMotionBand.WAITING, reducer.update(0f, 9_000L))
    }
}
