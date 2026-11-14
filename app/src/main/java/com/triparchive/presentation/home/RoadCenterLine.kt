package com.triparchive.presentation.home

import android.car.Car
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.triparchive.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlin.math.exp

private const val ROAD_HORIZON = 583f
private const val ROAD_BOTTOM = 1184f
private const val ROAD_CENTER = 420f
private const val DASH_COUNT = 7

/** Overlay uses the same source coordinates and centered Crop transform as the street painter. */
@Composable
internal fun Modifier.movingRoadCenterLine(): Modifier {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val roadPatch =
        remember(context.resources) {
            val original = BitmapFactory.decodeResource(context.resources, R.drawable.home_street_background)
            val patch = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
            // The baked marking is replaced by the road's horizontal gradient, row by row.
            // This leaves buildings, lane edges and the original resource untouched.
            for (y in ROAD_HORIZON.toInt() until original.height) {
                val halfWidth = (4 + (y - ROAD_HORIZON) * 0.14f).toInt()
                val left = ROAD_CENTER.toInt() - halfWidth
                val right = ROAD_CENTER.toInt() + halfWidth
                val a = original.getPixel(left - 1, y)
                val b = original.getPixel(right + 1, y)
                for (x in left..right) {
                    val t = (x - left).toFloat() / (right - left)

                    fun channel(shift: Int): Int {
                        val start = (a ushr shift) and 255
                        val end = (b ushr shift) and 255
                        return (start + (end - start) * t).toInt()
                    }
                    patch.setPixel(x, y, android.graphics.Color.rgb(channel(16), channel(8), channel(0)))
                }
            }
            original.recycle()
            patch.asImageBitmap()
        }
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
        val scale = maxOf(size.width / roadPatch.width, size.height / roadPatch.height)
        withTransform({
            translate((size.width - roadPatch.width * scale) / 2f, (size.height - roadPatch.height * scale) / 2f)
            scale(scale, scale, pivot = androidx.compose.ui.geometry.Offset.Zero)
        }) {
            drawImage(roadPatch)
            val path = Path()
            for (index in -1..DASH_COUNT) {
                val start = (index + phase.floatValue) / DASH_COUNT
                val end = start + 0.45f / DASH_COUNT
                if (end <= 0f || start >= 1f) continue
                val near = end.coerceIn(0f, 1f)
                val far = start.coerceIn(0f, 1f)
                val farDepth = far * far
                val nearDepth = near * near
                val y1 = ROAD_HORIZON + (ROAD_BOTTOM - ROAD_HORIZON) * farDepth
                val y2 = ROAD_HORIZON + (ROAD_BOTTOM - ROAD_HORIZON) * nearDepth
                val w1 = 1f + 58f * farDepth
                val w2 = 1f + 58f * nearDepth
                path.reset()
                path.moveTo(ROAD_CENTER - w1, y1)
                path.lineTo(ROAD_CENTER + w1, y1)
                path.lineTo(ROAD_CENTER + w2, y2)
                path.lineTo(ROAD_CENTER - w2, y2)
                path.close()
                drawPath(path, Color(0xFFFFF9E8))
            }
            // Paired road-surface markings stay visible beside the character. They share
            // the center line's world phase, and spread outward toward the viewer.
            val sideDashCount = DASH_COUNT * 2
            for (index in -1..sideDashCount) {
                val start = (index + (phase.floatValue * 2f) % 1f) / sideDashCount
                val end = start + 0.45f / sideDashCount
                if (end <= 0f || start >= 1f) continue
                val farDepth = start.coerceIn(0f, 1f).let { it * it }
                val nearDepth = end.coerceIn(0f, 1f).let { it * it }
                val farDistance = (ROAD_BOTTOM - ROAD_HORIZON) * farDepth
                val nearDistance = (ROAD_BOTTOM - ROAD_HORIZON) * nearDepth
                val farWidth = 0.8f + 7f * farDepth
                val nearWidth = 0.8f + 7f * nearDepth
                for (side in listOf(-1f, 1f)) {
                    val farX = ROAD_CENTER + side * farDistance * 1.5f
                    val nearX = ROAD_CENTER + side * nearDistance * 1.5f
                    path.reset()
                    path.moveTo(farX - farWidth, ROAD_HORIZON + farDistance)
                    path.lineTo(farX + farWidth, ROAD_HORIZON + farDistance)
                    path.lineTo(nearX + nearWidth, ROAD_HORIZON + nearDistance)
                    path.lineTo(nearX - nearWidth, ROAD_HORIZON + nearDistance)
                    path.close()
                    drawPath(path, Color(0xBFFFFFF0))
                }
            }
        }
    }
}
