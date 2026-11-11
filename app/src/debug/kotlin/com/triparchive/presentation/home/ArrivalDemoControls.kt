package com.triparchive.presentation.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.triparchive.data.navigation.DestinationState

@Suppress("UNUSED_PARAMETER")
@Composable
internal fun ArrivalDemoControls(
    destination: DestinationState,
    isSimulating: Boolean,
    canSimulate: Boolean,
    onArrive: (String) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) = Unit
