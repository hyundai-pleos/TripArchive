package com.triparchive.presentation

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

internal val LocalDrivingRestricted = staticCompositionLocalOf { false }

/** Shares the AAOS interaction restriction across this single-activity navigation graph. */
@Composable
internal fun DrivingUxRestrictionsProvider(content: @Composable () -> Unit) {
    if (LocalInspectionMode.current) {
        CompositionLocalProvider(LocalDrivingRestricted provides false, content = content)
        return
    }
    val context = LocalContext.current.applicationContext
    val drivingRestricted by rememberDrivingRestrictionState(context)
    CompositionLocalProvider(LocalDrivingRestricted provides drivingRestricted, content = content)
}

@Composable
private fun rememberDrivingRestrictionState(context: android.content.Context): androidx.compose.runtime.State<Boolean> {
    var drivingRestricted by androidx.compose.runtime.remember(context) { mutableStateOf(true) }
    DisposableEffect(context) {
        val handler = Handler(Looper.getMainLooper())
        var car: Car? = null
        var restrictionsManager: CarUxRestrictionsManager? = null
        var disposed = false
        val listener =
            CarUxRestrictionsManager.OnUxRestrictionsChangedListener { restrictions ->
                handler.post {
                    if (!disposed) drivingRestricted = restrictions.isRequiresDistractionOptimization
                }
            }
        val lifecycleListener =
            Car.CarServiceLifecycleListener { connectedCar, ready ->
                handler.post {
                    if (disposed) return@post
                    if (!ready) {
                        unregisterSafely(restrictionsManager)
                        restrictionsManager = null
                        drivingRestricted = true
                    } else {
                        try {
                            unregisterSafely(restrictionsManager)
                            val manager = connectedCar.getCarManager(CarUxRestrictionsManager::class.java)
                            restrictionsManager = manager
                            drivingRestricted =
                                manager.currentCarUxRestrictions?.isRequiresDistractionOptimization ?: true
                            manager.registerListener(listener)
                        } catch (exception: RuntimeException) {
                            drivingRestricted = true
                            Log.w(TAG, "Unable to read car UX restrictions", exception)
                        }
                    }
                }
            }

        try {
            car = Car.createCar(context, handler, 0L, lifecycleListener)
        } catch (exception: RuntimeException) {
            drivingRestricted = true
            Log.w(TAG, "Unable to connect to car UX restrictions", exception)
        }
        onDispose {
            disposed = true
            unregisterSafely(restrictionsManager)
            restrictionsManager = null
            car?.disconnect()
            car = null
        }
    }
    return androidx.compose.runtime.rememberUpdatedState(drivingRestricted)
}

private const val TAG = "DrivingUxRestrictions"

private fun unregisterSafely(manager: CarUxRestrictionsManager?) {
    try {
        manager?.unregisterListener()
    } catch (exception: RuntimeException) {
        Log.w(TAG, "Unable to unregister car UX restriction listener", exception)
    }
}
