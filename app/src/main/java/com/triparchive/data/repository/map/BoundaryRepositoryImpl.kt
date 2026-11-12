package com.triparchive.data.repository.map

import android.content.Context
import com.triparchive.domain.map.AdministrativeBoundaries
import com.triparchive.domain.map.AdministrativeBoundary
import com.triparchive.domain.map.BoundaryPolygon
import com.triparchive.domain.map.BoundaryRepository
import com.triparchive.domain.map.GeoPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

class BoundaryRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : BoundaryRepository {
        override suspend fun getBoundaries(): AdministrativeBoundaries =
            withContext(Dispatchers.IO) {
                AdministrativeBoundaries(
                    provinces = load("sido.json"),
                    districts = load("sgg.json"),
                )
            }

        private fun load(fileName: String): List<AdministrativeBoundary> {
            val json =
                context.assets
                    .open("administrative_boundaries/$fileName")
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }

            return Json.parseToJsonElement(json).jsonArray.map { feature ->
                val values = feature.jsonArray
                val polygons =
                    values[2].jsonArray.map { polygon ->
                        BoundaryPolygon(
                            rings =
                                polygon.jsonArray.map { ring ->
                                    ring.jsonArray.map { point ->
                                        val coordinates = point.jsonArray
                                        GeoPoint(
                                            longitude = coordinates[0].jsonPrimitive.double,
                                            latitude = coordinates[1].jsonPrimitive.double,
                                        )
                                    }
                                },
                        )
                    }
                AdministrativeBoundary(
                    code = values[0].jsonPrimitive.content,
                    name = values[1].jsonPrimitive.content,
                    polygons = polygons,
                )
            }
        }
    }
