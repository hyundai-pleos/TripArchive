package com.triparchive.presentation.home

import android.car.Car
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlin.math.exp

private const val DASH_COUNT = 7

/** Responsive scenery and speed-driven markings share the same road perspective. */
@Composable
internal fun Modifier.movingRoadCenterLine(): Modifier {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val speed = remember { mutableFloatStateOf(0f) }
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(context, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // ProfileCharacter owns the permission prompt. It may finish after this background
            // enters composition, so do not connect once without permission and remain unsubscribed.
            while (
                ContextCompat.checkSelfPermission(context, Car.PERMISSION_SPEED) != PackageManager.PERMISSION_GRANTED
            ) {
                delay(250)
            }
            val monitor =
                VehicleMotionMonitor(context) { state ->
                    speed.floatValue = state.speedKph?.takeIf { it.isFinite() }?.coerceIn(0f, 130f) ?: 0f
                }
            monitor.start()
            var previous = 0L
            var easedSpeed = 0f
            try {
                while (isActive) {
                    if (speed.floatValue <= 0f) {
                        easedSpeed = 0f
                        previous = 0L
                        snapshotFlow { speed.floatValue }.first { it > 0f }
                    }
                    withFrameNanos { now ->
                        val dt = if (previous == 0L) 0f else ((now - previous) / 1_000_000_000f).coerceAtMost(0.05f)
                        previous = now
                        val target = speed.floatValue
                        // Stop immediately for stationary/stale readings; gently follow acceleration.
                        easedSpeed =
                            if (target <= 0f) {
                                0f
                            } else {
                                easedSpeed + (target - easedSpeed) * (1f - exp(-dt / 0.35f))
                            }
                        if (easedSpeed > 0f) phase.floatValue = (phase.floatValue + dt * easedSpeed / 150f) % 1f
                    }
                }
            } finally {
                monitor.close()
                speed.floatValue = 0f
            }
        }
    }
    return drawBehind {
        val horizon = size.height * if (size.width < size.height) 0.65f else 0.58f
        val center = size.width / 2f
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFFE1FAF5), Color(0xFFF1FBF8))),
        )
        val road =
            Path().apply {
                moveTo(center, horizon)
                lineTo(size.width, horizon + size.height * 0.08f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                lineTo(0f, horizon + size.height * 0.08f)
                close()
            }
        drawRect(
            Color(0xFFD6EBE2),
            topLeft =
                Offset(0f, horizon),
        )
        drawPath(road, Color(0xFFC7D8DD))
        // Small, low-contrast trees stay at the edges at every window aspect ratio.
        for ((x, height) in listOf(0.045f to 0.055f, 0.09f to 0.03f, 0.91f to 0.03f, 0.955f to 0.055f)) {
            val h = size.height * height
            val base =
                Offset(size.width * x, horizon + h * 0.15f)
            drawLine(Color(0xFFB3C9B9), base, base.copy(y = base.y - h), strokeWidth = h * 0.1f)
            drawCircle(Color(0xFFB9DECA), radius = h * 0.36f, center = base.copy(y = base.y - h * 0.75f))
            drawCircle(
                Color(0xFFCAE7D5),
                radius = h * 0.24f,
                center =
                    base.copy(
                        x = base.x - h * 0.12f,
                        y =
                            base.y - h * 0.85f,
                    ),
            )
        }
        val path = Path()
        for (index in -1..DASH_COUNT) {
            val start = (index + phase.floatValue) / DASH_COUNT
            val end = start + 0.4f / DASH_COUNT
            if (end <= 0f || start >= 1f) continue
            val far = start.coerceIn(0f, 1f).let { it * it }
            val near = end.coerceIn(0f, 1f).let { it * it }
            val y1 = horizon + (size.height - horizon) * far
            val y2 = horizon + (size.height - horizon) * near
            // Side lanes retain visible movement when the character covers the center.
            for (lane in listOf(-1f, 0f, 1f)) {
                val x1 = center + lane * size.width * 0.65f * far
                val x2 = center + lane * size.width * 0.65f * near
                val w1 = size.width * (0.0005f + 0.004f * far)
                val w2 = size.width * (0.0005f + 0.004f * near)
                path.reset()
                path.moveTo(x1 - w1, y1)
                path.lineTo(x1 + w1, y1)
                path.lineTo(x2 + w2, y2)
                path.lineTo(x2 - w2, y2)
                path.close()
                drawPath(path, Color(0xBFEFF3E9))
            }
        }
    }
}
