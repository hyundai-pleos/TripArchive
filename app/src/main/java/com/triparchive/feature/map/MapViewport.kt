package com.triparchive.feature.map

private const val MIN_ZOOM = 1f
private const val DEFAULT_ZOOM = 1f
private const val DISTRICT_ZOOM_THRESHOLD = 3.16f
private const val MAX_ZOOM = 10f
private const val ZOOM_FACTOR = 1.77828f

internal data class MapViewport(
    val zoom: Float = DEFAULT_ZOOM,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val viewportWidth: Float = 0f,
    val viewportHeight: Float = 0f,
) {
    val showsDistricts: Boolean get() = zoom >= DISTRICT_ZOOM_THRESHOLD

    fun reduce(action: VisitMapAction): MapViewport =
        when (action) {
            is VisitMapAction.Transform -> {
                transform(action)
            }

            is VisitMapAction.ViewportResized -> {
                copy(
                    viewportWidth = action.width,
                    viewportHeight = action.height,
                ).constrained()
            }

            VisitMapAction.ZoomIn -> {
                stepZoom((zoom * ZOOM_FACTOR).coerceAtMost(MAX_ZOOM))
            }

            VisitMapAction.ZoomOut -> {
                stepZoom((zoom / ZOOM_FACTOR).coerceAtLeast(MIN_ZOOM))
            }

            VisitMapAction.ResetViewport -> {
                copy(zoom = DEFAULT_ZOOM, offsetX = 0f, offsetY = 0f)
            }

            VisitMapAction.Retry -> {
                this
            }
        }

    private fun stepZoom(newZoom: Float): MapViewport {
        val ratio = newZoom / zoom
        return copy(zoom = newZoom, offsetX = offsetX * ratio, offsetY = offsetY * ratio).constrained()
    }

    private fun transform(action: VisitMapAction.Transform): MapViewport {
        val newZoom = (zoom * action.scaleChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val ratio = newZoom / zoom
        val focalX = action.centroidX - action.viewportWidth / 2f
        val focalY = action.centroidY - action.viewportHeight / 2f
        return copy(
            zoom = newZoom,
            offsetX = focalX - (focalX - offsetX) * ratio + action.panX,
            offsetY = focalY - (focalY - offsetY) * ratio + action.panY,
            viewportWidth = action.viewportWidth,
            viewportHeight = action.viewportHeight,
        ).constrained()
    }

    private fun constrained(): MapViewport {
        val maxX = viewportWidth.coerceAtLeast(0f) * (zoom - MIN_ZOOM) / 2f
        val maxY = viewportHeight.coerceAtLeast(0f) * (zoom - MIN_ZOOM) / 2f
        return copy(offsetX = offsetX.coerceIn(-maxX, maxX), offsetY = offsetY.coerceIn(-maxY, maxY))
    }
}
