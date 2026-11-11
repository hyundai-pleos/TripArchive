package com.triparchive.feature.map

import com.triparchive.domain.map.AdministrativeBoundary
import com.triparchive.domain.map.GeoPoint

internal data class BoundarySegment(
    val start: GeoPoint,
    val end: GeoPoint,
)

internal fun findEmphasizedBorderSegments(
    regions: List<AdministrativeBoundary>,
    includeProvinceBorders: Boolean,
): List<BoundarySegment> {
    val segments = mutableMapOf<BoundarySegment, SegmentAdjacency>()

    for (region in regions) {
        for (polygon in region.polygons) {
            for ((ringIndex, ring) in polygon.rings.withIndex()) {
                for (index in ring.indices) {
                    val start = ring[index]
                    val end = ring[(index + 1) % ring.size]
                    if (start == end) continue

                    // Neighboring regions share vertices and may traverse the edge in opposite directions.
                    val segment = undirectedSegment(start, end)
                    segments
                        .getOrPut(segment) { SegmentAdjacency(region.code) }
                        .add(region.code, isOuterRing = ringIndex == 0)
                }
            }
        }
    }
    return segments.mapNotNull { (segment, adjacency) ->
        val isCountryOutline = adjacency.onOuterRing && !adjacency.isShared
        segment.takeIf { isCountryOutline || (includeProvinceBorders && adjacency.crossesProvince) }
    }
}

private class SegmentAdjacency(
    private val firstRegionCode: String,
) {
    private val firstProvinceCode = firstRegionCode.take(2)
    var onOuterRing = false
        private set
    var isShared = false
        private set
    var crossesProvince = false
        private set

    fun add(
        regionCode: String,
        isOuterRing: Boolean,
    ) {
        onOuterRing = onOuterRing || isOuterRing
        isShared = isShared || regionCode != firstRegionCode
        crossesProvince = crossesProvince || !regionCode.startsWith(firstProvinceCode)
    }
}

private fun undirectedSegment(
    start: GeoPoint,
    end: GeoPoint,
): BoundarySegment =
    if (start.longitude < end.longitude || (start.longitude == end.longitude && start.latitude < end.latitude)) {
        BoundarySegment(start, end)
    } else {
        BoundarySegment(end, start)
    }
