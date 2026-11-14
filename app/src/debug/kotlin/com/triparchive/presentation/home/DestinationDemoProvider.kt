package com.triparchive.presentation.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.triparchive.data.navigation.DestinationState

@Composable
internal fun rememberDestinationDemoInput(profileId: Long): DestinationDemoInput {
    val destination = remember(profileId) { mutableStateOf<DestinationState?>(null) }
    return remember(destination) {
        DestinationDemoInput(
            destination = destination,
            enabled = false,
            selectDestination = { _, _ -> },
            cancelRoute = {},
            arrive = {},
            acknowledge = {},
            reset = {},
        )
    }
}
