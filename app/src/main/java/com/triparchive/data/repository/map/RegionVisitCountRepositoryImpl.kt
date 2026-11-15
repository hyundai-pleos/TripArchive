package com.triparchive.data.repository.map

import com.triparchive.data.record.RecordDao
import com.triparchive.domain.map.AdministrativeBoundary
import com.triparchive.domain.map.BoundaryPolygon
import com.triparchive.domain.map.GeoPoint
import com.triparchive.domain.map.RegionVisitCountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RegionVisitCountRepositoryImpl
    @Inject
    constructor(
        private val records: RecordDao,
    ) : RegionVisitCountRepository {
        override fun observeDistrictVisitCounts(districts: List<AdministrativeBoundary>): Flow<Map<String, Int>> =
            records
                .observeStopLocationCounts()
                .map { stops ->
                    buildMap {
                        for (stop in stops) {
                            val point = GeoPoint(stop.longitude, stop.latitude)
                            val district =
                                districts.firstOrNull {
                                    it.polygons.any { polygon ->
                                        polygon.contains(point)
                                    }
                                }
                            if (district != null) {
                                put(district.code, (get(district.code) ?: 0) + stop.count)
                            }
                        }
                    }
                }.flowOn(Dispatchers.Default)
    }

private fun BoundaryPolygon.contains(point: GeoPoint): Boolean =
    rings.firstOrNull()?.containsPoint(point) == true && rings.drop(1).none { it.containsPoint(point) }

private fun List<GeoPoint>.containsPoint(point: GeoPoint): Boolean {
    var previous = lastOrNull() ?: return false
    var inside = false
    for (current in this) {
        // A horizontal ray crosses the ring only when its endpoints straddle the latitude.
        if ((previous.latitude > point.latitude) != (current.latitude > point.latitude)) {
            val intersection =
                previous.longitude + (current.longitude - previous.longitude) *
                    (point.latitude - previous.latitude) / (current.latitude - previous.latitude)
            if (point.longitude < intersection) inside = !inside
        }
        previous = current
    }
    return inside
}
