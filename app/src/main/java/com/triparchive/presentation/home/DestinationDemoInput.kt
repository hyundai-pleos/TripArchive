package com.triparchive.presentation.home

import androidx.compose.runtime.State
import com.triparchive.domain.navigation.DestinationCategory
import com.triparchive.domain.navigation.DestinationState

/** Build-specific destination input used by the demo controls and the adb demo receiver. */
internal data class DestinationDemoInput(
    val destination: State<DestinationState?>,
    val enabled: Boolean,
    val selectDestination: (String, DestinationCategory?) -> Unit,
    val cancelRoute: () -> Unit,
    val arrive: () -> Unit,
    val acknowledge: () -> Unit,
    val reset: () -> Unit,
)
