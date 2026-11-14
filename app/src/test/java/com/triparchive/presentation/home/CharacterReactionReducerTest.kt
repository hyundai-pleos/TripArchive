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
        val waiting = reducer.update(10f, 36_001L)
        assertEquals(CharacterReaction.WAITING, waiting.reaction)
        assertEquals(1f, waiting.morphWeights[CharacterReaction.WAITING.ordinal], 0.001f)
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
    fun expressionTargetsApplyImmediatelyWithoutBlending() {
        val reducer = CharacterReactionReducer()

        val firstMovingFrame = reducer.update(40f, 0L)
        assertEquals(5, firstMovingFrame.morphWeights.size)
        assertEquals(CharacterReaction.MOVING, firstMovingFrame.reaction)
        assertEquals(0.3f, firstMovingFrame.morphWeights[CharacterReaction.MOVING.ordinal], 0.001f)
        assertEquals(0f, firstMovingFrame.morphWeights[CharacterReaction.CALM.ordinal], 0.001f)
        assertEquals(
            firstMovingFrame.morphWeights.toList(),
            reducer.update(40f, 100L).morphWeights.toList(),
        )
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

    @Test
    fun nestedSpeedThresholdsKeepIndependentDwellClocks() {
        val descending = CharacterReactionReducer()
        descending.update(105f, 0L)
        descending.update(105f, 2_000L)
        assertEquals(CharacterReactionSpeedBand.KPH_60, descending.update(100f, 3_000L).speedBand)

        val ascending = CharacterReactionReducer()
        ascending.update(65f, 0L)
        ascending.update(105f, 2_000L)
        assertEquals(CharacterReactionSpeedBand.KPH_60, ascending.update(105f, 3_000L).speedBand)
        assertEquals(CharacterReactionSpeedBand.KPH_100, ascending.update(105f, 5_000L).speedBand)
    }

    @Test
    fun invalidInputOverridesWindAndRecoveryRestartsEntryDwell() {
        listOf(null, Float.NaN, Float.POSITIVE_INFINITY, -1f).forEach { invalid ->
            val reducer = CharacterReactionReducer()
            reducer.update(105f, 0L)
            assertEquals(CharacterReaction.WIND, reducer.update(105f, 3_000L).reaction)
            assertEquals(CharacterReaction.CALM, reducer.update(invalid, 3_100L).reaction)
            assertEquals(CharacterReactionSpeedBand.STANDARD, reducer.speedBand)
            assertTrue(reducer.update(invalid, 3_750L).morphWeights.all { it == 0f })
            reducer.update(105f, 5_000L)
            assertEquals(CharacterReactionSpeedBand.STANDARD, reducer.update(105f, 7_999L).speedBand)
            assertEquals(CharacterReactionSpeedBand.KPH_100, reducer.update(105f, 8_000L).speedBand)
            assertEquals(CharacterReaction.WIND, reducer.update(105f, 8_100L).reaction)
        }
    }

    @Test
    fun sixtyBandChangesMovingWeightImmediatelyAfterFiveSecondDisplayHold() {
        val reducer = CharacterReactionReducer()
        reducer.update(40f, 0L)
        reducer.update(65f, 1000L)
        val entry = reducer.update(65f, 4000L)
        assertEquals(CharacterReaction.MOVING, entry.reaction)
        assertEquals(0.3f, entry.morphWeights[CharacterReaction.MOVING.ordinal], 0.001f)
        assertEquals(0.3f, reducer.update(65f, 4999L).morphWeights[CharacterReaction.MOVING.ordinal], 0.001f)
        assertEquals(1f, reducer.update(65f, 5000L).morphWeights[CharacterReaction.MOVING.ordinal], 0.001f)
        reducer.update(55f, 6000L)
        assertEquals(1f, reducer.update(55f, 9999L).morphWeights[CharacterReaction.MOVING.ordinal], 0.001f)
        assertEquals(0.3f, reducer.update(55f, 10000L).morphWeights[CharacterReaction.MOVING.ordinal], 0.001f)
    }

    @Test
    fun windWaitsFiveSecondsAfterMovingWeightChangedButStopOverridesImmediately() {
        val reducer = CharacterReactionReducer()
        reducer.update(40f, 0L)
        reducer.update(65f, 1000L)
        assertEquals(0.3f, reducer.update(65f, 4000L).morphWeights[CharacterReaction.MOVING.ordinal], 0.001f)
        assertEquals(1f, reducer.update(65f, 5000L).morphWeights[CharacterReaction.MOVING.ordinal], 0.001f)
        reducer.update(105f, 6000L)
        assertEquals(CharacterReaction.MOVING, reducer.update(105f, 8999L).reaction)
        assertEquals(CharacterReaction.WIND, reducer.update(105f, 10000L).reaction)
        assertEquals(CharacterReaction.CALM, reducer.update(0f, 10001L).reaction)
        assertTrue(reducer.update(0f, 10001L).morphWeights.all { it == 0f })
    }
}
