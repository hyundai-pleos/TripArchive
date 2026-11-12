package com.triparchive.feature.map

import com.triparchive.domain.map.AdministrativeBoundaries
import com.triparchive.domain.map.VisitIntensity

internal data class VisitMapUiState(
    val content: VisitMapContent = VisitMapContent.Loading,
    val viewport: MapViewport = MapViewport(),
)

internal sealed interface VisitMapContent {
    data object Loading : VisitMapContent

    data class Error(
        val secondsUntilRetry: Int,
        val retrying: Boolean = false,
    ) : VisitMapContent

    data class Ready(
        val boundaries: AdministrativeBoundaries,
        val visitIntensities: Map<String, VisitIntensity>,
    ) : VisitMapContent
}

internal sealed interface VisitMapAction {
    data class ViewportResized(
        val width: Float,
        val height: Float,
    ) : VisitMapAction

    data class Transform(
        val scaleChange: Float,
        val panX: Float,
        val panY: Float,
        val centroidX: Float,
        val centroidY: Float,
        val viewportWidth: Float,
        val viewportHeight: Float,
    ) : VisitMapAction

    data object ZoomIn : VisitMapAction

    data object ZoomOut : VisitMapAction

    data object ResetViewport : VisitMapAction

    data object Retry : VisitMapAction
}
