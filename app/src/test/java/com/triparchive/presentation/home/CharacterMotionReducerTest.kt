package com.triparchive.presentation.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterMotionReducerTest {
    @Test
    fun movingBandChangesWaitForStableSpeedAndUseHysteresis() {
        val reducer = CharacterMotionReducer()

        assertEquals(CharacterMotionBand.UNAVAILABLE, reducer.update(5f, 0L))
        assertEquals(CharacterMotionBand.UNAVAILABLE, reducer.update(5f, 999L))
        assertEquals(CharacterMotionBand.LOW_SPEED, reducer.update(5f, 1_000L))

        assertEquals(CharacterMotionBand.LOW_SPEED, reducer.update(30f / 3.6f, 2_000L))
        assertEquals(CharacterMotionBand.LOW_SPEED, reducer.update(32f / 3.6f, 2_500L))
        assertEquals(CharacterMotionBand.LOW_SPEED, reducer.update(33f / 3.6f, 3_000L))
        assertEquals(CharacterMotionBand.GENERAL_DRIVING, reducer.update(33f / 3.6f, 4_000L))

        assertEquals(CharacterMotionBand.GENERAL_DRIVING, reducer.update(29f / 3.6f, 5_000L))
        assertEquals(CharacterMotionBand.GENERAL_DRIVING, reducer.update(26f / 3.6f, 5_500L))
        assertEquals(CharacterMotionBand.LOW_SPEED, reducer.update(26f / 3.6f, 6_500L))
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
        assertEquals(300L, reducer.pendingTransitionDelayMillis(7_000L))
        assertEquals(CharacterMotionBand.LOW_SPEED, reducer.update(0f, 7_299L))
        assertEquals(CharacterMotionBand.WAITING, reducer.update(0f, 7_300L))
    }

    @Test
    fun animationPlaybackUsesCappedGaitSpeedAndFallsBackToIdle() {
        assertEquals(CharacterAnimationClip.IDLE, CharacterMotionPresentation().animationClip)
        assertEquals(0.7f, animationSpeedFor(CharacterAnimationClip.WALK, 2f), 0.001f)
        assertEquals(1f, animationSpeedFor(CharacterAnimationClip.WALK, 90f), 0.001f)
        assertEquals(0.8f, animationSpeedFor(CharacterAnimationClip.RUN, 30f), 0.001f)
        assertEquals(1.1f, animationSpeedFor(CharacterAnimationClip.RUN, 80f), 0.001f)
        assertEquals(1.1f, animationSpeedFor(CharacterAnimationClip.RUN, 150f), 0.001f)
    }

    @Test
    fun clipChangesCrossFadeForHalfASecondAndSmoothSpeedChangesPerFrame() {
        val timeline = CharacterAnimationTimeline()

        val initial = timeline.advance(CharacterAnimationClip.IDLE, 0.35f, 0f)
        assertEquals(null, initial.previousClip)
        assertEquals(0.35f, initial.speedMultiplier, 0.001f)

        val firstWalkFrame = timeline.advance(CharacterAnimationClip.WALK, 1f, 0f)
        assertEquals(CharacterAnimationClip.WALK, firstWalkFrame.clip)
        assertEquals(CharacterAnimationClip.IDLE, firstWalkFrame.previousClip)
        assertEquals(0f, firstWalkFrame.crossFadeAlpha, 0.001f)
        assertEquals(0.35f, firstWalkFrame.speedMultiplier, 0.001f)

        val smoothedWalkFrame = timeline.advance(CharacterAnimationClip.WALK, 1f, 0.1f)
        assertEquals(CharacterAnimationClip.IDLE, smoothedWalkFrame.previousClip)
        assertEquals(0.2f, smoothedWalkFrame.crossFadeAlpha, 0.001f)
        assertTrue(smoothedWalkFrame.speedMultiplier > firstWalkFrame.speedMultiplier)
        assertTrue(smoothedWalkFrame.speedMultiplier < 1f)

        var blendFrame = smoothedWalkFrame
        repeat(3) {
            blendFrame = timeline.advance(CharacterAnimationClip.WALK, 1f, 0.1f)
        }
        assertEquals(CharacterAnimationClip.IDLE, blendFrame.previousClip)
        assertEquals(0.8f, blendFrame.crossFadeAlpha, 0.001f)

        val finishedBlend = timeline.advance(CharacterAnimationClip.WALK, 1f, 0.1f)
        assertEquals(null, finishedBlend.previousClip)
        assertEquals(1f, finishedBlend.crossFadeAlpha, 0.001f)

        val runFrame = timeline.advance(CharacterAnimationClip.RUN, 1.1f, 0f)
        assertEquals(CharacterAnimationClip.RUN, runFrame.clip)
        assertEquals(CharacterAnimationClip.WALK, runFrame.previousClip)
        assertEquals(0f, runFrame.crossFadeAlpha, 0.001f)
    }

    @Test
    fun morphGaitWeightsBlendFromPreviousClipToCurrentClip() {
        val walkWave = gaitMorphWave(CharacterAnimationClip.WALK, 1.08f / 4f, 1.08f)
        val runWave = gaitMorphWave(CharacterAnimationClip.RUN, 0.66f / 4f, 0.66f)
        assertEquals(0.72f, walkWave, 0.001f)
        assertEquals(1f, runWave, 0.001f)
        assertEquals(0f, gaitMorphWave(CharacterAnimationClip.IDLE, 0.25f, 1f), 0.001f)

        val atStart = blendedGaitMorphWeights(runWave, walkWave, 0f)
        assertEquals(walkWave, atStart[0], 0.001f)
        assertEquals(-walkWave, atStart[1], 0.001f)
        val halfway = blendedGaitMorphWeights(runWave, walkWave, 0.5f)
        assertEquals(0.86f, halfway[0], 0.001f)
        assertEquals(-0.86f, halfway[2], 0.001f)
        val atEnd = blendedGaitMorphWeights(runWave, walkWave, 1f)
        assertEquals(runWave, atEnd[0], 0.001f)
    }
}
