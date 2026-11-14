package com.triparchive.presentation.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.triparchive.data.navigation.DestinationCategory
import com.triparchive.data.navigation.DestinationState

private const val ACTION_DESTINATION_INPUT = "com.triparchive.demo.DESTINATION_INPUT"
private const val EXTRA_NAME = "name"
private const val EXTRA_CATEGORY = "category"
private const val EXTRA_ROUTING = "routing"
private const val EXTRA_ARRIVED = "arrived"
private const val EXTRA_RESET = "reset"

@Composable
internal fun rememberDestinationDemoInput(profileId: Long): DestinationDemoInput {
    val context = LocalContext.current.applicationContext
    val destination = remember(profileId) { mutableStateOf<DestinationState?>(null) }
    DisposableEffect(context, destination) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?,
                ) {
                    val demoIntent = intent ?: return
                    if (demoIntent.action != ACTION_DESTINATION_INPUT) return
                    if (demoIntent.getBooleanExtra(EXTRA_RESET, false)) {
                        destination.value = null
                        return
                    }
                    destination.value = demoIntent.toDemoDestinationState(destination.value)
                }
            }
        val filter = IntentFilter(ACTION_DESTINATION_INPUT)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    return remember(destination) {
        DestinationDemoInput(
            destination = destination,
            enabled = true,
            selectDestination = { name, category ->
                destination.value =
                    DestinationState()
                        .routeChanged(true)
                        .destinationChanged(name, category)
            },
            cancelRoute = {
                destination.value = destination.value?.routeChanged(false) ?: DestinationState()
            },
            arrive = {
                val state = destination.value
                val name = state?.name
                if (state?.routing == true && name != null) destination.value = state.arrive(name)
            },
            acknowledge = { destination.value = destination.value?.acknowledge() },
            reset = { destination.value = null },
        )
    }
}

private fun Intent.toDemoDestinationState(previous: DestinationState?): DestinationState {
    val hasName = hasExtra(EXTRA_NAME)
    val hasCategory = hasExtra(EXTRA_CATEGORY)
    val rawName = if (hasName) getStringExtra(EXTRA_NAME) else previous?.name
    val category =
        when {
            hasCategory -> getStringExtra(EXTRA_CATEGORY)?.toDestinationCategory()
            hasName && rawName != previous?.name -> null
            else -> previous?.category
        }
    var state = previous ?: DestinationState()
    if (hasName || hasCategory) {
        state =
            state.copy(
                name = rawName?.takeIf(String::isNotBlank),
                category = category.takeIf { !rawName.isNullOrBlank() },
                arrived = null,
                arrivedCategory = null,
                routing = true,
            )
    }
    if (getBooleanExtra(EXTRA_ARRIVED, false)) {
        return if (state.routing && state.name != null) state.arrive(state.name) else state
    }
    if (hasExtra(EXTRA_ROUTING) && !getBooleanExtra(EXTRA_ROUTING, true)) {
        return state.routeChanged(false)
    }
    if (getBooleanExtra(EXTRA_ROUTING, false)) {
        state = state.routeChanged(true)
    }
    return state
}

private fun String.toDestinationCategory(): DestinationCategory? =
    when (trim().lowercase()) {
        "mart" -> DestinationCategory.MART
        "school" -> DestinationCategory.SCHOOL
        "tourism" -> DestinationCategory.TOURISM
        "restaurant" -> DestinationCategory.RESTAURANT
        "hospital" -> DestinationCategory.HOSPITAL
        else -> null
    }
