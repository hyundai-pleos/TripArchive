package com.triparchive.presentation.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.triparchive.domain.map.VisitIntensity
import com.triparchive.presentation.LocalDrivingRestricted

private val EmphasizedBorderWidth = 1.5.dp
private val EmphasizedBorderHaloWidth = 3.dp

@Composable
internal fun BoundaryCanvas(
    paths: MapRenderData,
    visitIntensities: Map<String, VisitIntensity>,
    viewport: () -> MapViewport,
    onAction: (VisitMapAction) -> Unit,
    modifier: Modifier = Modifier,
    interactionsEnabled: Boolean = !LocalDrivingRestricted.current,
) {
    val currentOnAction = rememberUpdatedState(onAction)
    val fills =
        remember(visitIntensities) {
            visitIntensities.mapValues { (_, intensity) -> VisitMapPalette.fill(intensity) }
        }

    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    currentOnAction.value(VisitMapAction.ViewportResized(size.width.toFloat(), size.height.toFloat()))
                }.pointerInput(interactionsEnabled) {
                    if (interactionsEnabled) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            currentOnAction.value(
                                VisitMapAction.Transform(
                                    scaleChange = zoom,
                                    panX = pan.x,
                                    panY = pan.y,
                                    centroidX = centroid.x,
                                    centroidY = centroid.y,
                                    viewportWidth = size.width.toFloat(),
                                    viewportHeight = size.height.toFloat(),
                                ),
                            )
                        }
                    }
                },
    ) {
        val currentViewport = viewport()
        val (zoom, offsetX, offsetY) = currentViewport
        val showsDistricts = currentViewport.showsDistricts
        val boundaries = if (showsDistricts) paths.districts else paths.provinces
        val emphasizedBorders = if (showsDistricts) paths.detailedBorders else paths.countryOutline
        val boundaryColor = if (showsDistricts) VisitMapPalette.districtBoundary else VisitMapPalette.boundary
        val stroke = Stroke(width = (if (showsDistricts) 0.75.dp else 1.dp).toPx() / zoom)

        withTransform({
            translate(offsetX, offsetY)
            scale(zoom, zoom)
        }) {
            boundaries.forEach { boundary ->
                drawPath(path = boundary.path, color = fills[boundary.code] ?: VisitMapPalette.unvisited)
            }
            boundaries.forEach { boundary ->
                drawPath(path = boundary.path, color = boundaryColor, style = stroke)
            }
            drawPath(
                path = emphasizedBorders,
                color = VisitMapPalette.emphasizedBorderHalo,
                style = Stroke(width = EmphasizedBorderHaloWidth.toPx() / zoom, cap = StrokeCap.Round),
            )
            drawPath(
                path = emphasizedBorders,
                color = VisitMapPalette.emphasizedBorder,
                style = Stroke(width = EmphasizedBorderWidth.toPx() / zoom, cap = StrokeCap.Round),
            )
        }
    }
}
