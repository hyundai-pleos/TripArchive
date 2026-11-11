package com.triparchive.domain.map

import javax.inject.Inject

data class GeoPoint(
    val longitude: Double,
    val latitude: Double,
)

data class AdministrativeBoundary(
    val code: String,
    val name: String,
    val polygons: List<BoundaryPolygon>,
)

data class BoundaryPolygon(
    val rings: List<List<GeoPoint>>,
)

data class AdministrativeBoundaries(
    val provinces: List<AdministrativeBoundary>,
    val districts: List<AdministrativeBoundary>,
)

interface BoundaryRepository {
    suspend fun getBoundaries(): AdministrativeBoundaries
}

class GetAdministrativeBoundaries
    @Inject
    constructor(
        private val repository: BoundaryRepository,
    ) {
        suspend operator fun invoke(): AdministrativeBoundaries = repository.getBoundaries()
    }
