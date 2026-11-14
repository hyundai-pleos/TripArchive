package com.triparchive.presentation.home

import kotlin.math.abs

internal enum class CharacterMotionBand {
    UNAVAILABLE,
    WAITING,
    LOW_SPEED,
    GENERAL_DRIVING,
}

internal data class CharacterMotionPresentation(
    val band: CharacterMotionBand = CharacterMotionBand.UNAVAILABLE,
    val drivingRestricted: Boolean = true,
    val parked: Boolean = false,
) {
    val idleEnabled: Boolean
        get() = band == CharacterMotionBand.WAITING && parked && !drivingRestricted

    val staticPoseTimeSeconds: Float
        get() {
            if (drivingRestricted) return 0f
            return when (band) {
                CharacterMotionBand.LOW_SPEED -> 0.62f
                CharacterMotionBand.GENERAL_DRIVING -> 1.34f
                else -> 0f
            }
        }
}

/** Applies hysteresis at 30 km/h and waits before changing between moving speed bands. */
internal class CharacterMotionReducer(
    initialBand: CharacterMotionBand = CharacterMotionBand.UNAVAILABLE,
) {
    var band: CharacterMotionBand = initialBand
        private set

    private var pendingBand: CharacterMotionBand? = null
    private var pendingSinceMillis: Long = 0

    fun pendingTransitionDelayMillis(nowMillis: Long): Long? {
        if (pendingBand == null) return null
        return (MOVING_STATE_DWELL_MILLIS - (nowMillis - pendingSinceMillis)).coerceAtLeast(0L)
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
            if (speedKph <= WAITING_SPEED_KPH) {
                CharacterMotionBand.WAITING
            } else {
                movingBandFor(speedKph)
            }

        if (target == CharacterMotionBand.WAITING || band == CharacterMotionBand.WAITING) {
            return changeImmediately(target)
        }
        if (target == band) {
            pendingBand = null
            return band
        }

        if (pendingBand != target) {
            pendingBand = target
            pendingSinceMillis = nowMillis
        } else if (nowMillis - pendingSinceMillis >= MOVING_STATE_DWELL_MILLIS) {
            band = target
            pendingBand = null
        }
        return band
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

    private companion object {
        const val METERS_PER_SECOND_TO_KPH = 3.6f
        const val WAITING_SPEED_KPH = 3f
        const val GENERAL_DRIVING_BASE_KPH = 30f
        const val GENERAL_DRIVING_ENTER_KPH = 33f
        const val LOW_SPEED_ENTER_KPH = 27f
        const val MOVING_STATE_DWELL_MILLIS = 2_000L
    }
}
