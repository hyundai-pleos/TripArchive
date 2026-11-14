package com.triparchive.presentation.home

import kotlin.math.abs
import kotlin.math.exp

internal enum class CharacterMotionBand {
    UNAVAILABLE,
    WAITING,
    LOW_SPEED,
    GENERAL_DRIVING,
}

internal enum class CharacterAnimationClip(
    val assetName: String,
) {
    IDLE("Idle"),
    WALK("Walk"),
    RUN("Run"),
}

internal data class CharacterMotionPresentation(
    val band: CharacterMotionBand = CharacterMotionBand.UNAVAILABLE,
    val drivingRestricted: Boolean = true,
    val stationaryInPark: Boolean = false,
    val speedKph: Float? = null,
) {
    val idleEnabled: Boolean
        get() = band == CharacterMotionBand.WAITING && stationaryInPark && !drivingRestricted

    val animationClip: CharacterAnimationClip
        get() =
            when (band) {
                CharacterMotionBand.LOW_SPEED -> CharacterAnimationClip.WALK

                CharacterMotionBand.GENERAL_DRIVING -> CharacterAnimationClip.RUN

                CharacterMotionBand.UNAVAILABLE,
                CharacterMotionBand.WAITING,
                -> CharacterAnimationClip.IDLE
            }

    val targetAnimationSpeed: Float
        get() = animationSpeedFor(animationClip, speedKph)
}

internal data class CharacterAnimationFrame(
    val clip: CharacterAnimationClip,
    val clipTimeSeconds: Float,
    val speedMultiplier: Float,
    val previousClip: CharacterAnimationClip? = null,
    val previousTimeSeconds: Float = 0f,
    /** The current clip's blend weight. */
    val crossFadeAlpha: Float = 1f,
)

/** Smooths gait playback per render frame and blends clip changes over half a second. */
internal class CharacterAnimationTimeline(
    initialClip: CharacterAnimationClip = CharacterAnimationClip.IDLE,
) {
    private var currentClip = initialClip
    private var currentTimeSeconds = 0f
    private var currentSpeedMultiplier = IDLE_ANIMATION_SPEED
    private var previousClip: CharacterAnimationClip? = null
    private var previousTimeSeconds = 0f
    private var previousSpeedMultiplier = IDLE_ANIMATION_SPEED
    private var crossFadeElapsedSeconds = CROSS_FADE_SECONDS

    fun advance(
        targetClip: CharacterAnimationClip,
        targetSpeedMultiplier: Float,
        deltaSeconds: Float,
    ): CharacterAnimationFrame {
        val delta = deltaSeconds.takeIf { it.isFinite() }?.coerceIn(0f, MAX_FRAME_DELTA_SECONDS) ?: 0f
        val safeTargetSpeed =
            targetSpeedMultiplier.takeIf { it.isFinite() }?.coerceIn(0f, MAX_PLAYBACK_SPEED) ?: IDLE_ANIMATION_SPEED

        if (targetClip != currentClip) {
            previousClip = currentClip
            previousTimeSeconds = currentTimeSeconds
            previousSpeedMultiplier = currentSpeedMultiplier
            currentClip = targetClip
            currentTimeSeconds = 0f
            crossFadeElapsedSeconds = 0f
        }

        // Exponential smoothing keeps the speed response consistent across frame rates.
        val smoothing = 1f - exp((-delta / SPEED_SMOOTHING_SECONDS).toDouble()).toFloat()
        currentSpeedMultiplier += (safeTargetSpeed - currentSpeedMultiplier) * smoothing
        currentSpeedMultiplier = currentSpeedMultiplier.coerceIn(0f, MAX_PLAYBACK_SPEED)
        currentTimeSeconds += delta * currentSpeedMultiplier

        val blendingFrom = previousClip
        val blendAlpha =
            if (blendingFrom == null) {
                1f
            } else {
                previousTimeSeconds += delta * previousSpeedMultiplier
                crossFadeElapsedSeconds = (crossFadeElapsedSeconds + delta).coerceAtMost(CROSS_FADE_SECONDS)
                (crossFadeElapsedSeconds / CROSS_FADE_SECONDS).coerceIn(0f, 1f)
            }
        if (blendingFrom != null && blendAlpha >= 1f) previousClip = null

        return CharacterAnimationFrame(
            clip = currentClip,
            clipTimeSeconds = currentTimeSeconds,
            speedMultiplier = currentSpeedMultiplier,
            previousClip = blendingFrom.takeIf { blendAlpha < 1f },
            previousTimeSeconds = previousTimeSeconds,
            crossFadeAlpha = blendAlpha,
        )
    }

    private companion object {
        const val IDLE_ANIMATION_SPEED = 0.35f
        const val MAX_PLAYBACK_SPEED = 1.1f
        const val SPEED_SMOOTHING_SECONDS = 0.24f
        const val CROSS_FADE_SECONDS = 0.5f
        const val MAX_FRAME_DELTA_SECONDS = 0.1f
    }
}

internal fun animationSpeedFor(
    clip: CharacterAnimationClip,
    speedKph: Float?,
): Float {
    if (clip == CharacterAnimationClip.IDLE) return 0.35f
    val speed = speedKph?.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: return 0.35f
    return when (clip) {
        CharacterAnimationClip.IDLE -> {
            0.35f
        }

        CharacterAnimationClip.WALK -> {
            interpolate(
                value = speed.coerceIn(WALK_SPEED_MIN_KPH, WALK_SPEED_CAP_KPH),
                inputMin = WALK_SPEED_MIN_KPH,
                inputMax = WALK_SPEED_CAP_KPH,
                outputMin = WALK_SPEED_MIN_MULTIPLIER,
                outputMax = WALK_SPEED_MAX_MULTIPLIER,
            )
        }

        CharacterAnimationClip.RUN -> {
            interpolate(
                value = speed.coerceIn(RUN_SPEED_MIN_KPH, RUN_SPEED_CAP_KPH),
                inputMin = RUN_SPEED_MIN_KPH,
                inputMax = RUN_SPEED_CAP_KPH,
                outputMin = RUN_SPEED_MIN_MULTIPLIER,
                outputMax = RUN_SPEED_MAX_MULTIPLIER,
            )
        }
    }
}

private fun interpolate(
    value: Float,
    inputMin: Float,
    inputMax: Float,
    outputMin: Float,
    outputMax: Float,
): Float {
    val fraction = ((value - inputMin) / (inputMax - inputMin)).coerceIn(0f, 1f)
    return outputMin + (outputMax - outputMin) * fraction
}

private const val WALK_SPEED_MIN_KPH = 2f
private const val WALK_SPEED_CAP_KPH = 30f
private const val WALK_SPEED_MIN_MULTIPLIER = 0.7f
private const val WALK_SPEED_MAX_MULTIPLIER = 1f
private const val RUN_SPEED_MIN_KPH = 30f
private const val RUN_SPEED_CAP_KPH = 80f
private const val RUN_SPEED_MIN_MULTIPLIER = 0.8f
private const val RUN_SPEED_MAX_MULTIPLIER = 1.1f

/** Applies hysteresis at 30 km/h and waits before changing between moving speed bands. */
internal class CharacterMotionReducer(
    initialBand: CharacterMotionBand = CharacterMotionBand.UNAVAILABLE,
) {
    var band: CharacterMotionBand = initialBand
        private set

    private var pendingBand: CharacterMotionBand? = null
    private var pendingSinceMillis: Long = 0

    fun pendingTransitionDelayMillis(nowMillis: Long): Long? {
        val pending = pendingBand ?: return null
        return (dwellMillisFor(pending) - (nowMillis - pendingSinceMillis)).coerceAtLeast(0L)
    }

    fun update(
        speedMetersPerSecond: Float?,
        nowMillis: Long,
    ): CharacterMotionBand {
        if (speedMetersPerSecond == null || !speedMetersPerSecond.isFinite()) {
            return changeImmediately(CharacterMotionBand.UNAVAILABLE)
        }

        val speedKph = abs(speedMetersPerSecond) * METERS_PER_SECOND_TO_KPH
        val target =
            if (speedKph <= waitingThresholdKph()) {
                CharacterMotionBand.WAITING
            } else {
                movingBandFor(speedKph)
            }

        if (band == CharacterMotionBand.UNAVAILABLE && target == CharacterMotionBand.WAITING) {
            return changeImmediately(target)
        }
        if (target == band) {
            pendingBand = null
            return band
        }

        if (pendingBand != target) {
            pendingBand = target
            pendingSinceMillis = nowMillis
        } else if (nowMillis - pendingSinceMillis >= dwellMillisFor(target)) {
            band = target
            pendingBand = null
        }
        return band
    }

    private fun waitingThresholdKph(): Float =
        when (band) {
            CharacterMotionBand.WAITING -> 4f
            CharacterMotionBand.UNAVAILABLE -> 3f
            else -> 2f
        }

    private fun movingBandFor(speedKph: Float): CharacterMotionBand {
        val threshold =
            when (band) {
                CharacterMotionBand.LOW_SPEED -> GENERAL_DRIVING_ENTER_KPH
                CharacterMotionBand.GENERAL_DRIVING -> LOW_SPEED_ENTER_KPH
                else -> GENERAL_DRIVING_BASE_KPH
            }
        return if (speedKph >= threshold) {
            CharacterMotionBand.GENERAL_DRIVING
        } else {
            CharacterMotionBand.LOW_SPEED
        }
    }

    private fun changeImmediately(next: CharacterMotionBand): CharacterMotionBand {
        pendingBand = null
        band = next
        return band
    }

    private fun dwellMillisFor(target: CharacterMotionBand): Long =
        if (target == CharacterMotionBand.WAITING) WAITING_STATE_DWELL_MILLIS else MOVING_STATE_DWELL_MILLIS

    private companion object {
        const val METERS_PER_SECOND_TO_KPH = 3.6f
        const val GENERAL_DRIVING_BASE_KPH = 30f
        const val GENERAL_DRIVING_ENTER_KPH = 33f
        const val LOW_SPEED_ENTER_KPH = 27f
        const val MOVING_STATE_DWELL_MILLIS = 1_000L
        const val WAITING_STATE_DWELL_MILLIS = 300L
    }
}
