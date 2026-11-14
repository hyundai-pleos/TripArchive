package com.triparchive.presentation.home

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat

/** Reads the standard AAOS speed, gear and driving restriction signals for the active home. */
internal class VehicleMotionMonitor(
    context: Context,
    private val onPresentationChanged: (CharacterMotionPresentation) -> Unit,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val reducer = CharacterMotionReducer()

    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null
    private var restrictionsManager: CarUxRestrictionsManager? = null
    private var speedMetersPerSecond: Float? = null
    private var gearSelection: Int? = null
    private var drivingRestricted = true
    private var closed = false
    private var pendingStateChange: Runnable? = null
    private var lastPresentation = CharacterMotionPresentation()
    private var lastSpeedUpdateElapsedMillis: Long? = null
    private var speedFreshnessTimeout: Runnable? = null

    private val propertyCallback =
        object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                mainHandler.post {
                    when (value.propertyId) {
                        VehiclePropertyIds.PERF_VEHICLE_SPEED -> {
                            if (value.status == CarPropertyValue.STATUS_AVAILABLE) {
                                speedMetersPerSecond = (value.value as? Number)?.toFloat()
                                lastSpeedUpdateElapsedMillis = SystemClock.elapsedRealtime()
                                scheduleSpeedFreshnessTimeout()
                            } else {
                                speedMetersPerSecond = null
                                clearSpeedFreshnessTimeout()
                            }
                        }

                        VehiclePropertyIds.GEAR_SELECTION -> {
                            gearSelection =
                                if (value.status == CarPropertyValue.STATUS_AVAILABLE) {
                                    (value.value as? Number)?.toInt()
                                } else {
                                    null
                                }
                        }
                    }
                    updateState()
                }
            }

            override fun onErrorEvent(
                propertyId: Int,
                areaId: Int,
            ) {
                mainHandler.post {
                    when (propertyId) {
                        VehiclePropertyIds.PERF_VEHICLE_SPEED -> speedMetersPerSecond = null
                        VehiclePropertyIds.GEAR_SELECTION -> gearSelection = null
                    }
                    Log.w(TAG, "Vehicle property error for id=$propertyId area=$areaId")
                    if (propertyId == VehiclePropertyIds.PERF_VEHICLE_SPEED) clearSpeedFreshnessTimeout()
                    updateState()
                }
            }
        }

    private val restrictionsListener =
        CarUxRestrictionsManager.OnUxRestrictionsChangedListener { restrictions ->
            mainHandler.post {
                drivingRestricted = restrictions.isRequiresDistractionOptimization
                updateState()
            }
        }

    private val lifecycleListener =
        Car.CarServiceLifecycleListener { connectedCar, ready ->
            mainHandler.post {
                if (closed) return@post
                if (!ready) {
                    unregisterVehicleListeners()
                    speedMetersPerSecond = null
                    gearSelection = null
                    drivingRestricted = true
                    updateState()
                    Log.w(TAG, "Vehicle service disconnected")
                } else {
                    registerVehicleListeners(connectedCar)
                }
            }
        }

    fun start() {
        if (closed || car != null) return
        try {
            car = Car.createCar(appContext, mainHandler, 0L, lifecycleListener)
        } catch (exception: RuntimeException) {
            Log.e(TAG, "Unable to connect to vehicle service", exception)
            publishUnavailable()
        }
    }

    fun close() {
        if (closed) return
        closed = true
        pendingStateChange?.let(mainHandler::removeCallbacks)
        clearSpeedFreshnessTimeout()
        unregisterVehicleListeners()
        car?.disconnect()
        car = null
    }

    private fun registerVehicleListeners(connectedCar: Car) {
        unregisterVehicleListeners()
        try {
            propertyManager = connectedCar.getCarManager(CarPropertyManager::class.java)
            registerSpeed()
            registerGear()
        } catch (exception: RuntimeException) {
            Log.e(TAG, "Unable to register vehicle properties", exception)
            speedMetersPerSecond = null
        }

        try {
            restrictionsManager = connectedCar.getCarManager(CarUxRestrictionsManager::class.java)
            val restrictions = restrictionsManager?.currentCarUxRestrictions
            if (restrictions != null) {
                drivingRestricted = restrictions.isRequiresDistractionOptimization
            }
            restrictionsManager?.registerListener(restrictionsListener)
        } catch (exception: RuntimeException) {
            drivingRestricted = true
            Log.w(TAG, "Driving restrictions unavailable, keeping character static", exception)
        }
        updateState()
    }

    private fun registerSpeed() {
        val manager = propertyManager ?: return
        val hasPermission =
            ContextCompat.checkSelfPermission(appContext, Car.PERMISSION_SPEED) ==
                PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            speedMetersPerSecond = null
            Log.w(TAG, "CAR_SPEED permission is not granted")
            return
        }
        try {
            if (!manager.registerCallback(
                    propertyCallback,
                    VehiclePropertyIds.PERF_VEHICLE_SPEED,
                    CarPropertyManager.SENSOR_RATE_NORMAL,
                )
            ) {
                Log.w(TAG, "Vehicle speed property is unavailable")
                speedMetersPerSecond = null
                return
            }
            speedMetersPerSecond =
                if (manager.isPropertyAvailable(VehiclePropertyIds.PERF_VEHICLE_SPEED, 0)) {
                    manager.getFloatProperty(VehiclePropertyIds.PERF_VEHICLE_SPEED, 0)
                } else {
                    null
                }
            if (speedMetersPerSecond != null) {
                lastSpeedUpdateElapsedMillis = SystemClock.elapsedRealtime()
                scheduleSpeedFreshnessTimeout()
            }
        } catch (exception: RuntimeException) {
            speedMetersPerSecond = null
            Log.w(TAG, "Could not read vehicle speed", exception)
        }
    }

    private fun registerGear() {
        val manager = propertyManager ?: return
        val hasPermission =
            ContextCompat.checkSelfPermission(appContext, Car.PERMISSION_POWERTRAIN) ==
                PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            gearSelection = null
            Log.w(TAG, "CAR_POWERTRAIN permission is not granted")
            return
        }
        try {
            if (!manager.registerCallback(
                    propertyCallback,
                    VehiclePropertyIds.GEAR_SELECTION,
                    CarPropertyManager.SENSOR_RATE_ONCHANGE,
                )
            ) {
                Log.w(TAG, "Gear selection property is unavailable")
                gearSelection = null
                return
            }
            gearSelection =
                if (manager.isPropertyAvailable(VehiclePropertyIds.GEAR_SELECTION, 0)) {
                    manager.getIntProperty(VehiclePropertyIds.GEAR_SELECTION, 0)
                } else {
                    null
                }
        } catch (exception: RuntimeException) {
            gearSelection = null
            Log.w(TAG, "Could not read gear selection", exception)
        }
    }

    private fun updateState() {
        if (closed) return
        val band =
            reducer.update(
                speedMetersPerSecond = speedMetersPerSecond,
                nowMillis = SystemClock.elapsedRealtime(),
            )
        val presentation =
            CharacterMotionPresentation(
                band = band,
                drivingRestricted = drivingRestricted,
                parked = gearSelection == VehicleGear.GEAR_PARK,
            )
        if (presentation != lastPresentation) publishPresentation(presentation)

        pendingStateChange?.let(mainHandler::removeCallbacks)
        pendingStateChange = null
        val remainingMillis = reducer.pendingTransitionDelayMillis(SystemClock.elapsedRealtime())
        if (remainingMillis != null) {
            pendingStateChange = Runnable { updateState() }
            mainHandler.postDelayed(pendingStateChange!!, remainingMillis)
        }
    }

    private fun publishUnavailable() {
        speedMetersPerSecond = null
        drivingRestricted = true
        updateState()
    }

    private fun publishPresentation(presentation: CharacterMotionPresentation) {
        lastPresentation = presentation
        val pose =
            when {
                presentation.idleEnabled -> "parked idle"
                presentation.staticPoseTimeSeconds == 0f -> "neutral static"
                else -> "sampled static"
            }
        Log.i(TAG, "Character motion state band=${presentation.band} pose=$pose restricted=$drivingRestricted")
        onPresentationChanged(presentation)
    }

    private fun unregisterVehicleListeners() {
        propertyManager?.let { manager ->
            try {
                manager.unregisterCallback(propertyCallback)
            } catch (exception: RuntimeException) {
                Log.w(TAG, "Could not unregister vehicle property listener", exception)
            }
        }
        propertyManager = null
        restrictionsManager?.let { manager ->
            try {
                manager.unregisterListener()
            } catch (exception: RuntimeException) {
                Log.w(TAG, "Could not unregister driving restriction listener", exception)
            }
        }
        restrictionsManager = null
    }

    private fun scheduleSpeedFreshnessTimeout() {
        speedFreshnessTimeout?.let(mainHandler::removeCallbacks)
        speedFreshnessTimeout =
            Runnable {
                val lastUpdate = lastSpeedUpdateElapsedMillis ?: return@Runnable
                if (SystemClock.elapsedRealtime() - lastUpdate >= SPEED_FRESHNESS_TIMEOUT_MILLIS) {
                    speedMetersPerSecond = null
                    clearSpeedFreshnessTimeout()
                    Log.w(TAG, "Vehicle speed updates became stale")
                    updateState()
                }
            }
        mainHandler.postDelayed(speedFreshnessTimeout!!, SPEED_FRESHNESS_TIMEOUT_MILLIS)
    }

    private fun clearSpeedFreshnessTimeout() {
        speedFreshnessTimeout?.let(mainHandler::removeCallbacks)
        speedFreshnessTimeout = null
        lastSpeedUpdateElapsedMillis = null
    }

    private companion object {
        const val TAG = "VehicleCharacterMotion"
        const val SPEED_FRESHNESS_TIMEOUT_MILLIS = 5_000L
    }
}
