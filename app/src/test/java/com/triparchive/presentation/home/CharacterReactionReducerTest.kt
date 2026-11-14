package com.triparchive.presentation.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterReactionReducerTest {
    @Test
    fun lowSpeedAloneAndAnInitialParkedStopDoNotBecomeWaiting() {
        val reducer = CharacterReactionReducer()

        assertEquals(CharacterReaction.CALM, reducer.update(0f, 0L, stationaryInPark = true).reaction)
        assertEquals(CharacterReaction.CALM, reducer.update(10f, 6_000L).reaction)
        assertEquals(CharacterReaction.CALM, reducer.update(10f, 40_000L).reaction)

        val stopped = CharacterReactionReducer()
        assertEquals(CharacterReaction.CALM, stopped.update(0f, 0L).reaction)
        assertEquals(CharacterReaction.CALM, stopped.update(0f, 30_000L).reaction)
    }

    @Test
    fun repeatedStopAndGoTrafficWaitsAfterThirtySecondsAndTwoStops() {
        val reducer = CharacterReactionReducer()

        assertEquals(CharacterReaction.CALM, reducer.update(0f, 0L, stationaryInPark = true).reaction)
        assertEquals(CharacterReaction.CALM, reducer.update(10f, 6_000L).reaction)
        assertEquals(CharacterReaction.CALM, reducer.update(0f, 15_000L).reaction)
        assertEquals(CharacterReaction.CALM, reducer.update(10f, 17_000L).reaction)
        assertEquals(CharacterReaction.CALM, reducer.update(10f, 26_000L).reaction)
        assertEquals(CharacterReaction.CALM, reducer.update(0f, 26_000L).reaction)
        assertEquals(CharacterReaction.CALM, reducer.update(0f, 35_999L).reaction)
        assertEquals(CharacterReaction.CALM, reducer.update(0f, 36_000L).reaction)
        assertEquals(CharacterReaction.WAITING, reducer.update(10f, 36_001L).reaction)
    }

    @Test
    fun flowRecoveryNeedsTwoStableSecondsThenJoyHoldsForFiveSeconds() {
        val reducer = CharacterReactionReducer()
        reducer.update(0f, 0L, stationaryInPark = true)
        reducer.update(10f, 6_000L)
        reducer.update(0f, 15_000L)
        reducer.update(10f, 17_000L)
        reducer.update(10f, 26_000L)
        reducer.update(0f, 26_000L)
        assertEquals(CharacterReaction.CALM, reducer.update(0f, 36_000L).reaction)
        assertEquals(CharacterReaction.WAITING, reducer.update(10f, 36_001L).reaction)

        assertEquals(CharacterReaction.WAITING, reducer.update(40f, 42_000L).reaction)
        assertEquals(CharacterReaction.WAITING, reducer.update(40f, 43_999L).reaction)
        assertEquals(CharacterReaction.JOY, reducer.update(40f, 44_000L).reaction)
        assertEquals(CharacterReaction.JOY, reducer.update(40f, 48_999L).reaction)
        assertEquals(CharacterReaction.MOVING, reducer.update(40f, 49_000L).reaction)

        val highSpeed = CharacterReactionReducer()
        assertFalse(highSpeed.update(105f, 0L).reaction == CharacterReaction.JOY)
        assertFalse(highSpeed.update(105f, 3_000L).reaction == CharacterReaction.JOY)
    }

    @Test
    fun sixtyAndHundredBandsUseThreeSecondEntryHysteresisAndExitThresholds() {
        val reducer = CharacterReactionReducer()

        reducer.update(60f, 0L)
        reducer.update(62f, 2_999L)
        assertEquals(CharacterReactionSpeedBand.STANDARD, reducer.speedBand)
        reducer.update(63f, 3_000L)
        reducer.update(63f, 5_999L)
        assertEquals(CharacterReactionSpeedBand.STANDARD, reducer.speedBand)
        assertEquals(CharacterReactionSpeedBand.KPH_60, reducer.update(63f, 6_000L).speedBand)
        assertEquals(CharacterReactionSpeedBand.KPH_60, reducer.update(59f, 7_000L).speedBand)
        assertEquals(CharacterReactionSpeedBand.KPH_60, reducer.update(57f, 11_000L).speedBand)
        assertEquals(CharacterReactionSpeedBand.STANDARD, reducer.update(56f, 11_001L).speedBand)

        val fastReducer = CharacterReactionReducer()
        fastReducer.update(63f, 0L)
        assertEquals(CharacterReactionSpeedBand.STANDARD, fastReducer.update(63f, 2_999L).speedBand)
        assertEquals(CharacterReactionSpeedBand.KPH_60, fastReducer.update(63f, 3_000L).speedBand)
        fastReducer.update(103f, 4_000L)
        assertEquals(CharacterReactionSpeedBand.KPH_60, fastReducer.update(103f, 6_999L).speedBand)
        assertEquals(CharacterReactionSpeedBand.KPH_100, fastReducer.update(103f, 7_000L).speedBand)
        assertEquals(CharacterReaction.MOVING, fastReducer.update(103f, 7_999L).reaction)
        assertEquals(CharacterReaction.WIND, fastReducer.update(103f, 8_000L).reaction)
        assertEquals(CharacterReactionSpeedBand.KPH_100, fastReducer.update(98f, 9_000L).speedBand)
        assertEquals(CharacterReactionSpeedBand.KPH_60, fastReducer.update(96f, 12_000L).speedBand)
        assertEquals(CharacterReaction.MOVING, fastReducer.update(96f, 13_000L).reaction)
    }

    @Test
    fun transitionsBlendFiveSemanticFaceTargetsOverSixHundredFiftyMilliseconds() {
        val reducer = CharacterReactionReducer()

        val atStart = reducer.update(40f, 0L)
        assertEquals(5, atStart.morphWeights.size)
        assertEquals(0f, atStart.morphWeights[CharacterReaction.MOVING.ordinal], 0.001f)

        val halfway = reducer.update(40f, 325L)
        assertTrue(halfway.morphWeights[CharacterReaction.MOVING.ordinal] in 0.39f..0.41f)
        val complete = reducer.update(40f, 650L)
        assertEquals(0.8f, complete.morphWeights[CharacterReaction.MOVING.ordinal], 0.001f)
        assertEquals(0f, complete.morphWeights[CharacterReaction.CALM.ordinal], 0.001f)
    }

    @Test
    fun minimumHoldAppliesToNormalChangesButStopAndMissingDataOverrideImmediately() {
        val reducer = CharacterReactionReducer()

        assertEquals(CharacterReaction.MOVING, reducer.update(40f, 0L).reaction)
        assertEquals(CharacterReaction.MOVING, reducer.update(10f, 1_000L).reaction)
        assertEquals(CharacterReaction.CALM, reducer.update(10f, 5_000L).reaction)
        assertEquals(CharacterReaction.CALM, reducer.update(40f, 6_000L).reaction)
        assertEquals(CharacterReaction.CALM, reducer.update(40f, 9_999L).reaction)
        assertEquals(CharacterReaction.MOVING, reducer.update(40f, 10_000L).reaction)
        assertEquals(CharacterReaction.CALM, reducer.update(0f, 10_001L).reaction)
        assertEquals(CharacterReaction.CALM, reducer.update(null, 10_002L).reaction)
    }
}
