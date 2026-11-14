package com.triparchive.presentation.home

/** Semantic morph target ordering shared with the generated character assets. */
internal enum class CharacterReaction {
    CALM,
    WAITING,
    JOY,
    MOVING,
    WIND,
}

internal enum class CharacterReactionSpeedBand {
    STANDARD,
    KPH_60,
    KPH_100,
}

internal data class CharacterReactionFrame(
    val reaction: CharacterReaction,
    val speedBand: CharacterReactionSpeedBand,
    val morphWeights: FloatArray,
)

/**
 * Chooses a restrained face expression from elapsed speed samples. Call [update] every frame with the
 * latest vehicle sample so dwell timers progress even when the speed service emits no new value.
 */
internal class CharacterReactionReducer(
    initialReaction: CharacterReaction = CharacterReaction.CALM,
) {
    var reaction: CharacterReaction = initialReaction
        private set

    var speedBand: CharacterReactionSpeedBand = CharacterReactionSpeedBand.STANDARD
        private set

    private var reactionEnteredAtMillis = Long.MIN_VALUE
    private var aboveSixtySinceMillis: Long? = null
    private var aboveHundredSinceMillis: Long? = null
    private var joyExpiresAtMillis: Long? = null
    private var flowRecoverySinceMillis: Long? = null

    private var congestionStartedAtMillis: Long? = null
    private var congestionStops = 0
    private var stoppedSinceMillis: Long? = null
    private var currentStopCounted = false
    private var congestionQualified = false

    private var transitionStartMillis = 0L
    private var transitionFromWeights = weightsFor(initialReaction)
    private var transitionToWeights = transitionFromWeights.copyOf()

    fun update(
        speedKph: Float?,
        nowMillis: Long,
        stationaryInPark: Boolean = false,
    ): CharacterReactionFrame {
        val speed = speedKph?.takeIf { it.isFinite() && it >= 0f }
        if (speed == null || stationaryInPark) {
            resetCongestion()
            resetSpeedBand(nowMillis)
            joyExpiresAtMillis = null
            changeReaction(CharacterReaction.CALM, nowMillis)
            return frameAt(nowMillis)
        }

        if (speed <= STOPPED_SPEED_KPH) {
            updateCongestion(speed, nowMillis)
            resetSpeedBand(nowMillis)
            flowRecoverySinceMillis = null
            joyExpiresAtMillis = null
            changeReaction(CharacterReaction.CALM, nowMillis)
            return frameAt(nowMillis)
        }

        if (reaction == CharacterReaction.WAITING && speed > TRAFFIC_WINDOW_EXIT_KPH &&
            speed < HIGH_SPEED_ENTER_60_KPH
        ) {
            val startedAt = flowRecoverySinceMillis ?: nowMillis.also { flowRecoverySinceMillis = it }
            if (nowMillis - startedAt < FLOW_RECOVERY_DWELL_MILLIS) return frameAt(nowMillis)
            requestReaction(CharacterReaction.JOY, nowMillis)
            if (reaction == CharacterReaction.JOY) {
                joyExpiresAtMillis = nowMillis + JOY_DURATION_MILLIS
                resetCongestion()
            }
            return frameAt(nowMillis)
        }

        flowRecoverySinceMillis = null
        if (reaction == CharacterReaction.WAITING && speed >= HIGH_SPEED_ENTER_60_KPH) {
            // A high-speed sample clears the traffic episode without using joy as a speed reward.
            resetCongestion()
        } else {
            updateCongestion(speed, nowMillis)
        }
        updateSpeedBand(speed, nowMillis)

        if (congestionQualified && speed <= TRAFFIC_WINDOW_EXIT_KPH) {
            joyExpiresAtMillis = null
            requestReaction(CharacterReaction.WAITING, nowMillis)
            return frameAt(nowMillis)
        }

        val joyDeadline = joyExpiresAtMillis
        if (reaction == CharacterReaction.JOY && joyDeadline != null && nowMillis < joyDeadline) {
            return frameAt(nowMillis)
        }
        if (reaction == CharacterReaction.JOY) joyExpiresAtMillis = null

        val desired =
            when {
                speedBand == CharacterReactionSpeedBand.KPH_100 -> {
                    CharacterReaction.WIND
                }

                speedBand == CharacterReactionSpeedBand.KPH_60 -> {
                    CharacterReaction.MOVING
                }

                speed >= HIGH_SPEED_ENTER_60_KPH && speedBand == CharacterReactionSpeedBand.STANDARD -> {
                    // Keep the last low-speed expression until the 60/100 km/h entry dwell completes.
                    reaction
                }

                speed > FLOWING_SPEED_KPH -> {
                    CharacterReaction.MOVING
                }

                else -> {
                    CharacterReaction.CALM
                }
            }
        requestReaction(desired, nowMillis)
        return frameAt(nowMillis)
    }

    private fun updateCongestion(
        speedKph: Float,
        nowMillis: Long,
    ) {
        if (speedKph > TRAFFIC_WINDOW_EXIT_KPH) {
            resetCongestion()
            return
        }

        if (congestionStartedAtMillis == null) congestionStartedAtMillis = nowMillis
        if (speedKph <= STOPPED_SPEED_KPH) {
            if (stoppedSinceMillis == null) {
                stoppedSinceMillis = nowMillis
                currentStopCounted = false
            }
            val stopDuration = nowMillis - (stoppedSinceMillis ?: nowMillis)
            if (!currentStopCounted && stopDuration >= MIN_TRAFFIC_STOP_MILLIS) {
                congestionStops += 1
                currentStopCounted = true
            }
        } else {
            val completedStopDuration = stoppedSinceMillis?.let { nowMillis - it } ?: 0L
            if (!currentStopCounted && completedStopDuration >= MIN_TRAFFIC_STOP_MILLIS) {
                congestionStops += 1
            }
            stoppedSinceMillis = null
            currentStopCounted = false
        }

        val elapsed = nowMillis - (congestionStartedAtMillis ?: nowMillis)
        congestionQualified = elapsed >= CONGESTION_WAIT_MILLIS && congestionStops >= MIN_TRAFFIC_STOPS
    }

    private fun updateSpeedBand(
        speedKph: Float,
        nowMillis: Long,
    ) {
        // Nested speed thresholds need independent clocks: dropping below 103 must not
        // discard time already spent above 63 km/h.
        aboveSixtySinceMillis =
            if (speedKph >= HIGH_SPEED_ENTER_60_KPH) aboveSixtySinceMillis ?: nowMillis else null
        aboveHundredSinceMillis =
            if (speedKph >= HIGH_SPEED_ENTER_100_KPH) aboveHundredSinceMillis ?: nowMillis else null
        val sixtyReady = aboveSixtySinceMillis?.let { nowMillis - it >= HIGH_SPEED_ENTRY_DWELL_MILLIS } == true
        val hundredReady = aboveHundredSinceMillis?.let { nowMillis - it >= HIGH_SPEED_ENTRY_DWELL_MILLIS } == true
        val next =
            when {
                speedBand == CharacterReactionSpeedBand.KPH_100 && speedKph >= HIGH_SPEED_EXIT_100_KPH -> {
                    CharacterReactionSpeedBand.KPH_100
                }

                hundredReady -> {
                    CharacterReactionSpeedBand.KPH_100
                }

                speedBand != CharacterReactionSpeedBand.STANDARD && speedKph >= HIGH_SPEED_EXIT_60_KPH -> {
                    CharacterReactionSpeedBand.KPH_60
                }

                sixtyReady -> {
                    CharacterReactionSpeedBand.KPH_60
                }

                else -> {
                    CharacterReactionSpeedBand.STANDARD
                }
            }
        if (next != speedBand) changeSpeedBand(next, nowMillis)
    }

    private fun requestReaction(
        target: CharacterReaction,
        nowMillis: Long,
    ) {
        if (target == reaction) {
            updateTransitionTarget(nowMillis)
            return
        }
        if (heldForMillis(reactionEnteredAtMillis, nowMillis) < MIN_REACTION_HOLD_MILLIS) return
        if (reactionEnteredAtMillis != Long.MIN_VALUE &&
            nowMillis - transitionStartMillis < MIN_REACTION_HOLD_MILLIS
        ) {
            return
        }
        changeReaction(target, nowMillis)
    }

    private fun changeReaction(
        next: CharacterReaction,
        nowMillis: Long,
    ) {
        if (next != reaction) {
            reaction = next
            reactionEnteredAtMillis = nowMillis
        }
        updateTransitionTarget(nowMillis)
    }

    private fun changeSpeedBand(
        next: CharacterReactionSpeedBand,
        nowMillis: Long,
    ) {
        speedBand = next
        updateTransitionTarget(nowMillis)
    }

    private fun updateTransitionTarget(nowMillis: Long) {
        val nextWeights = weightsFor(reaction)
        if (reaction == CharacterReaction.MOVING) {
            nextWeights[reaction.ordinal] = if (speedBand == CharacterReactionSpeedBand.STANDARD) 0.3f else 1f
        }
        if (nextWeights.contentEquals(transitionToWeights)) return
        if (reaction == CharacterReaction.MOVING &&
            transitionToWeights[CharacterReaction.MOVING.ordinal] > 0f &&
            nowMillis - transitionStartMillis < MIN_REACTION_HOLD_MILLIS
        ) {
            return
        }
        transitionFromWeights = interpolatedWeights(nowMillis)
        transitionToWeights = nextWeights
        transitionStartMillis = nowMillis
    }

    private fun resetSpeedBand(nowMillis: Long) {
        aboveSixtySinceMillis = null
        aboveHundredSinceMillis = null
        if (speedBand != CharacterReactionSpeedBand.STANDARD) {
            speedBand = CharacterReactionSpeedBand.STANDARD
            updateTransitionTarget(nowMillis)
        }
    }

    private fun resetCongestion() {
        congestionStartedAtMillis = null
        congestionStops = 0
        stoppedSinceMillis = null
        currentStopCounted = false
        congestionQualified = false
        flowRecoverySinceMillis = null
    }

    private fun frameAt(nowMillis: Long): CharacterReactionFrame =
        CharacterReactionFrame(reaction, speedBand, interpolatedWeights(nowMillis))

    private fun interpolatedWeights(nowMillis: Long): FloatArray {
        val progress =
            ((nowMillis - transitionStartMillis).toFloat() / FACE_TRANSITION_MILLIS)
                .coerceIn(0f, 1f)
        return FloatArray(REACTION_COUNT) { index ->
            transitionFromWeights[index] + (transitionToWeights[index] - transitionFromWeights[index]) * progress
        }
    }

    private fun heldForMillis(
        sinceMillis: Long,
        nowMillis: Long,
    ): Long = if (sinceMillis == Long.MIN_VALUE) Long.MAX_VALUE else (nowMillis - sinceMillis).coerceAtLeast(0L)

    private companion object {
        const val REACTION_COUNT = 5
        const val STOPPED_SPEED_KPH = 2f
        const val FLOWING_SPEED_KPH = 25f
        const val TRAFFIC_WINDOW_EXIT_KPH = 25f
        const val HIGH_SPEED_ENTER_60_KPH = 63f
        const val HIGH_SPEED_EXIT_60_KPH = 57f
        const val HIGH_SPEED_ENTER_100_KPH = 103f
        const val HIGH_SPEED_EXIT_100_KPH = 97f
        const val MIN_TRAFFIC_STOPS = 2
        const val MIN_TRAFFIC_STOP_MILLIS = 1_000L
        const val CONGESTION_WAIT_MILLIS = 30_000L
        const val HIGH_SPEED_ENTRY_DWELL_MILLIS = 3_000L
        const val MIN_REACTION_HOLD_MILLIS = 5_000L
        const val FLOW_RECOVERY_DWELL_MILLIS = 2_000L
        const val FACE_TRANSITION_MILLIS = 650f
        const val JOY_DURATION_MILLIS = 5_000L

        fun weightsFor(reaction: CharacterReaction): FloatArray {
            val weights = FloatArray(REACTION_COUNT)
            val weight =
                when (reaction) {
                    CharacterReaction.CALM -> 0f
                    CharacterReaction.WAITING -> 0.82f
                    CharacterReaction.JOY -> 1f
                    CharacterReaction.MOVING -> 0.3f
                    CharacterReaction.WIND -> 1f
                }
            weights[reaction.ordinal] = weight
            return weights
        }
    }
}
