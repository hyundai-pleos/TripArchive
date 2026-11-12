package com.triparchive.feature.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import com.triparchive.domain.map.AdministrativeBoundaries
import com.triparchive.domain.map.AdministrativeBoundary
import com.triparchive.domain.map.GeoPoint
import kotlin.math.cos
import kotlin.math.min

internal data class RenderedBoundary(
    val code: String,
    val path: Path,
)

internal data class MapRenderData(
    val provinces: List<RenderedBoundary>,
    val districts: List<RenderedBoundary>,
    val countryOutline: Path = Path(),
    val detailedBorders: Path = Path(),
)

internal object BoundaryPaths {
    fun create(
        boundaries: AdministrativeBoundaries,
        width: Float,
        height: Float,
    ): MapRenderData {
        if (width <= 0f || height <= 0f || boundaries.provinces.isEmpty()) {
            return MapRenderData(emptyList(), emptyList())
        }

        val xScale = cos(Math.toRadians(36.5)).toFloat()
        val allPoints = boundaries.provinces.flatMap { it.allPoints() }
        if (allPoints.isEmpty()) return MapRenderData(emptyList(), emptyList())

        val minX = allPoints.minOf { it.longitude.toFloat() * xScale }
        val maxX = allPoints.maxOf { it.longitude.toFloat() * xScale }
        val minY = allPoints.minOf { it.latitude.toFloat() }
        val maxY = allPoints.maxOf { it.latitude.toFloat() }
        val mapWidth = maxX - minX
        val mapHeight = maxY - minY
        if (mapWidth <= 0f || mapHeight <= 0f) return MapRenderData(emptyList(), emptyList())
        val fitScale = min(width * 0.86f / mapWidth, height * 0.86f / mapHeight)
        val centerX = width / 2f
        val centerY = height / 2f
        val mapCenterX = (minX + maxX) / 2f
        val mapCenterY = (minY + maxY) / 2f

        fun project(point: GeoPoint) =
            Offset(
                x = (point.longitude.toFloat() * xScale - mapCenterX) * fitScale + centerX,
                y = (mapCenterY - point.latitude.toFloat()) * fitScale + centerY,
            )

        fun render(features: List<AdministrativeBoundary>) =
            features.map { feature ->
                val path =
                    Path().apply {
                        fillType = PathFillType.EvenOdd
                        feature.polygons.forEach { polygon ->
                            polygon.rings.forEach { ring ->
                                addRing(ring, ::project)
                            }
                        }
                    }
                RenderedBoundary(feature.code, path)
            }

        fun renderEmphasizedBorders(
            regions: List<AdministrativeBoundary>,
            includeProvinceBorders: Boolean,
        ) = Path().apply {
            findEmphasizedBorderSegments(regions, includeProvinceBorders).forEach { (start, end) ->
                val (startX, startY) = project(start)
                val (endX, endY) = project(end)
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
        }

        return MapRenderData(
            provinces = render(boundaries.provinces),
            districts = render(boundaries.districts),
            countryOutline = renderEmphasizedBorders(boundaries.provinces, includeProvinceBorders = false),
            detailedBorders = renderEmphasizedBorders(boundaries.districts, includeProvinceBorders = true),
        )
    }

    private inline fun Path.addRing(
        points: List<GeoPoint>,
        project: (GeoPoint) -> Offset,
    ) {
        if (points.isEmpty()) return
        points.forEachIndexed { index, point ->
            val (x, y) = project(point)
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    private fun AdministrativeBoundary.allPoints() = polygons.flatMap { polygon -> polygon.rings.flatten() }
}
