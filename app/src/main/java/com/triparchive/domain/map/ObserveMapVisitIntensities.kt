package com.triparchive.domain.map

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveMapVisitIntensities
    @Inject
    constructor(
        private val repository: RegionVisitCountRepository,
    ) {
        operator fun invoke(boundaries: AdministrativeBoundaries): Flow<Map<String, VisitIntensity>> {
            val provinceCodes = boundaries.provinces.mapTo(hashSetOf()) { it.code }
            val provinceByDistrict =
                boundaries.districts.associate { district ->
                    district.code to district.code.take(2).takeIf { it in provinceCodes }
                }

            return repository
                .observeDistrictVisitCounts()
                .map { districtCounts ->
                    buildMap {
                        val provinceCounts = mutableMapOf<String, Long>()
                        districtCounts.forEach { (districtCode, count) ->
                            if (districtCode !in provinceByDistrict) return@forEach
                            val intensity = VisitIntensity.fromCount(count.toLong()) ?: return@forEach
                            put(districtCode, intensity)
                            provinceByDistrict[districtCode]?.let { provinceCode ->
                                provinceCounts[provinceCode] = provinceCounts.getOrDefault(provinceCode, 0L) + count
                            }
                        }
                        provinceCounts.forEach { (provinceCode, count) ->
                            VisitIntensity.fromCount(count)?.let { put(provinceCode, it) }
                        }
                    }
                }.distinctUntilChanged()
        }
    }
