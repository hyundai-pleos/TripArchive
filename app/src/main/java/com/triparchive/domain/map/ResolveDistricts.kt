package com.triparchive.domain.map

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs

/** Resolves destination coordinates against the same offline boundaries used by the map. */
class ResolveDistricts
    @Inject
    constructor(
        private val getBoundaries: GetAdministrativeBoundaries,
    ) {
        private val indexMutex = Mutex()
        private var cachedIndex: List<IndexedDistrict>? = null

        suspend operator fun invoke(points: List<GeoPoint?>): List<String?> {
            if (points.none { it.isValid() }) return List(points.size) { null }
            return withContext(Dispatchers.Default) {
                val index =
                    indexMutex.withLock {
                        cachedIndex ?: indexDistricts(getBoundaries().districts).also { cachedIndex = it }
                    }
                points.map { point ->
                    if (point.isValid()) resolvePoint(point!!, index) else null
                }
            }
        }
    }

private fun GeoPoint?.isValid(): Boolean =
    this != null && latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
        (latitude != 0.0 || longitude != 0.0)

private data class Bounds(
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double,
) {
    fun contains(point: GeoPoint): Boolean =
        point.longitude >= minX - EPSILON && point.longitude <= maxX + EPSILON &&
            point.latitude >= minY - EPSILON && point.latitude <= maxY + EPSILON
}

private data class IndexedPolygon(
    val bounds: Bounds,
    val rings: List<List<GeoPoint>>,
)

private data class IndexedDistrict(
    val code: String,
    val polygons: List<IndexedPolygon>,
)

private fun indexDistricts(districts: List<AdministrativeBoundary>): List<IndexedDistrict> =
    districts.map { district ->
        IndexedDistrict(
            code = district.code,
            polygons =
                district.polygons.mapNotNull { polygon ->
                    val outer = polygon.rings.firstOrNull()?.takeIf { it.size >= 3 } ?: return@mapNotNull null
                    IndexedPolygon(
                        bounds =
                            Bounds(
                                minX = outer.minOf { it.longitude },
                                maxX = outer.maxOf { it.longitude },
                                minY = outer.minOf { it.latitude },
                                maxY = outer.maxOf { it.latitude },
                            ),
                        rings = polygon.rings,
                    )
                },
        )
    }

private enum class PointRelation { OUTSIDE, INSIDE, BOUNDARY }

private fun resolvePoint(
    point: GeoPoint,
    districts: List<IndexedDistrict>,
): String? {
    var match: String? = null
    for (district in districts) {
        for (polygon in district.polygons) {
            if (!polygon.bounds.contains(point)) continue
            when (polygonRelation(point, polygon.rings)) {
                PointRelation.BOUNDARY -> {
                    return null
                }

                PointRelation.INSIDE -> {
                    if (match != null && match != district.code) return null
                    match = district.code
                }

                PointRelation.OUTSIDE -> {
                    Unit
                }
            }
        }
    }
    return match
}

private fun polygonRelation(
    point: GeoPoint,
    rings: List<List<GeoPoint>>,
): PointRelation {
    if (rings.isEmpty()) return PointRelation.OUTSIDE
    when (ringRelation(point, rings.first())) {
        PointRelation.OUTSIDE -> return PointRelation.OUTSIDE
        PointRelation.BOUNDARY -> return PointRelation.BOUNDARY
        PointRelation.INSIDE -> Unit
    }
    for (hole in rings.drop(1)) {
        when (ringRelation(point, hole)) {
            PointRelation.BOUNDARY -> return PointRelation.BOUNDARY
            PointRelation.INSIDE -> return PointRelation.OUTSIDE
            PointRelation.OUTSIDE -> Unit
        }
    }
    return PointRelation.INSIDE
}

private fun ringRelation(
    point: GeoPoint,
    ring: List<GeoPoint>,
): PointRelation {
    if (ring.size < 3) return PointRelation.OUTSIDE
    val x = point.longitude
    val y = point.latitude
    var inside = false
    var previous = ring.last()
    for (current in ring) {
        val dx = current.longitude - previous.longitude
        val dy = current.latitude - previous.latitude
        val cross = (x - previous.longitude) * dy - (y - previous.latitude) * dx
        val withinSegment =
            x >= minOf(previous.longitude, current.longitude) - EPSILON &&
                x <= maxOf(previous.longitude, current.longitude) + EPSILON &&
                y >= minOf(previous.latitude, current.latitude) - EPSILON &&
                y <= maxOf(previous.latitude, current.latitude) + EPSILON
        if (withinSegment && abs(cross) <= EPSILON) return PointRelation.BOUNDARY
        if ((previous.latitude > y) != (current.latitude > y)) {
            val intersectionX = previous.longitude + (y - previous.latitude) * dx / dy
            if (intersectionX > x) inside = !inside
        }
        previous = current
    }
    return if (inside) PointRelation.INSIDE else PointRelation.OUTSIDE
}

private const val EPSILON = 1e-10
