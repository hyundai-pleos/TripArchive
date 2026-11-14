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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.triparchive.domain.navigation.CategoryLookupStatus
import com.triparchive.domain.navigation.Destination
import com.triparchive.domain.navigation.DestinationCategory
import com.triparchive.domain.navigation.DestinationState

private const val ACTION_DESTINATION_INPUT = "com.triparchive.demo.DESTINATION_INPUT"

@Composable
internal fun rememberDestinationDemoInput(
    profileId: Long,
    createVisit: (Destination) -> Destination?,
): DestinationDemoInput {
    val context = LocalContext.current.applicationContext
    val destination = remember(profileId) { mutableStateOf<DestinationState?>(null) }
    val latestCreateVisit = rememberUpdatedState(createVisit)
    val input =
        remember(destination) {
            DestinationDemoInput(
                destination = destination,
                enabled = true,
                selectDestination = { name, category ->
                    if (name.isNotBlank()) {
                        val previous = destination.value ?: DestinationState()
                        val visit =
                            previous.current?.takeIf { it.name == name }
                                ?: latestCreateVisit.value(Destination(0, name, 0.0, 0.0))
                        if (visit != null) {
                            destination.value =
                                previous.copy(
                                    current =
                                        visit.copy(
                                            category = category,
                                            categoryStatus = CategoryLookupStatus.SUCCESS,
                                        ),
                                    routing = true,
                                )
                        }
                    }
                },
                cancelRoute = {
                    destination.value =
                        destination.value?.copy(current = null, routing = false) ?: DestinationState()
                },
                arrive = {
                    val state = destination.value
                    if (state?.routing == true && state.current != null) {
                        destination.value =
                            state.copy(
                                current = null,
                                pendingArrival = state.pendingArrival ?: state.current,
                                routing = false,
                            )
                    }
                },
                acknowledge = { destination.value = destination.value?.copy(pendingArrival = null) },
                reset = { destination.value = null },
            )
        }
    DisposableEffect(context, input) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?,
                ) {
                    val command = intent?.takeIf { it.action == ACTION_DESTINATION_INPUT } ?: return
                    if (command.getBooleanExtra("reset", false)) {
                        input.reset()
                        return
                    }
                    val current = input.destination.value?.current
                    if (command.hasExtra("name") || command.hasExtra("category")) {
                        val name = command.getStringExtra("name") ?: current?.name
                        val category =
                            if (command.hasExtra("category")) {
                                command.getStringExtra("category")?.toDestinationCategory()
                            } else {
                                current?.category?.takeIf { current.name == name }
                            }
                        if (name != null) input.selectDestination(name, category)
                    }
                    if (command.getBooleanExtra("arrived", false)) {
                        input.arrive()
                    } else if (command.hasExtra("routing") && !command.getBooleanExtra("routing", true)) {
                        input.cancelRoute()
                    }
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
    return input
}

private fun String.toDestinationCategory(): DestinationCategory? =
    when (trim().lowercase()) {
        "mart" -> DestinationCategory.MART
        "school" -> DestinationCategory.SCHOOL
        "tourism" -> DestinationCategory.TOURISM
        "food", "restaurant" -> DestinationCategory.FOOD
        "hospital" -> DestinationCategory.HOSPITAL
        else -> null
    }
