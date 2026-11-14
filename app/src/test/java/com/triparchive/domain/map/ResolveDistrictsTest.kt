package com.triparchive.domain.map

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveDistrictsTest {
    private fun point(
        x: Double,
        y: Double,
    ) = GeoPoint(x, y)

    private fun rectangle(
        minX: Double,
        minY: Double,
        maxX: Double,
        maxY: Double,
    ) = listOf(point(minX, minY), point(maxX, minY), point(maxX, maxY), point(minX, maxY), point(minX, minY))

    @Test
    fun handlesDistrictsHolesIslandsAndUncertainCoordinates() =
        runBlocking {
            val boundaries =
                AdministrativeBoundaries(
                    provinces = emptyList(),
                    districts =
                        listOf(
                            AdministrativeBoundary(
                                "A",
                                "A",
                                listOf(
                                    BoundaryPolygon(
                                        listOf(
                                            rectangle(127.0, 37.0, 131.0, 41.0),
                                            rectangle(128.0, 38.0, 129.0, 39.0),
                                        ),
                                    ),
                                    BoundaryPolygon(listOf(rectangle(136.0, 39.0, 137.0, 40.0))),
                                ),
                            ),
                            AdministrativeBoundary(
                                "B",
                                "B",
                                listOf(BoundaryPolygon(listOf(rectangle(131.0, 37.0, 135.0, 41.0)))),
                            ),
                        ),
                )
            var loads = 0
            val resolver =
                ResolveDistricts(
                    GetAdministrativeBoundaries(
                        object : BoundaryRepository {
                            override suspend fun getBoundaries(): AdministrativeBoundaries {
                                loads++
                                return boundaries
                            }
                        },
                    ),
                )
            val result =
                resolver(
                    listOf(
                        point(127.5, 37.5),
                        point(132.0, 38.0),
                        point(136.5, 39.5),
                        point(128.5, 38.5), // Hole is not part of the district.
                        point(128.0, 38.5), // Hole boundary is uncertain.
                        point(131.0, 38.0), // Shared district boundary is uncertain.
                        point(125.0, 36.0),
                        point(0.0, 0.0),
                        point(Double.NaN, 37.0),
                        point(190.0, 37.0),
                        null,
                    ),
                )
            assertEquals(listOf("A", "B", "A", null, null, null, null, null, null, null, null), result)
            assertEquals(listOf("A"), resolver(listOf(point(127.5, 37.5))))
            assertEquals(1, loads)
        }

    @Test
    fun overlappingDistrictsAreExcludedInsteadOfGuessing() =
        runBlocking {
            val resolver =
                ResolveDistricts(
                    GetAdministrativeBoundaries(
                        object : BoundaryRepository {
                            override suspend fun getBoundaries() =
                                AdministrativeBoundaries(
                                    emptyList(),
                                    listOf(
                                        AdministrativeBoundary(
                                            "A",
                                            "A",
                                            listOf(BoundaryPolygon(listOf(rectangle(127.0, 37.0, 130.0, 40.0)))),
                                        ),
                                        AdministrativeBoundary(
                                            "B",
                                            "B",
                                            listOf(BoundaryPolygon(listOf(rectangle(128.0, 38.0, 131.0, 41.0)))),
                                        ),
                                    ),
                                )
                        },
                    ),
                )
            assertEquals(
                listOf(null, "A", "B"),
                resolver(listOf(point(129.0, 39.0), point(127.5, 37.5), point(130.5, 40.5))),
            )
        }

    @Test
    fun entirelyInvalidInputsDoNotLoadBoundaries() =
        runBlocking {
            val resolver =
                ResolveDistricts(
                    GetAdministrativeBoundaries(
                        object : BoundaryRepository {
                            override suspend fun getBoundaries(): AdministrativeBoundaries = error("must not load")
                        },
                    ),
                )
            assertEquals(listOf(null, null), resolver(listOf(null, point(0.0, 0.0))))
        }
}
