package com.triparchive.data.repository.map

import com.triparchive.data.record.RecordDao
import com.triparchive.domain.map.GeoPoint
import com.triparchive.domain.map.RegionVisitCountRepository
import com.triparchive.domain.map.ResolveDistricts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RegionVisitCountRepositoryImpl
    @Inject
    constructor(
        private val recordDao: RecordDao,
        private val resolveDistricts: ResolveDistricts,
    ) : RegionVisitCountRepository {
        override fun observeDistrictVisitCounts(profileId: Long): Flow<Map<String, Int>> =
            recordDao
                .observeDistrictLocationVisitCounts(profileId)
                .map { counts ->
                    // Records saved before district codes were introduced can still have coordinates.
                    val unresolved = counts.filter { it.districtCode == null }
                    val legacyCodes =
                        resolveDistricts(
                            unresolved.map { count ->
                                if (count.latitude != null && count.longitude != null) {
                                    GeoPoint(count.longitude, count.latitude)
                                } else {
                                    null
                                }
                            },
                        ).iterator()
                    buildMap {
                        for (count in counts) {
                            val districtCode = count.districtCode ?: legacyCodes.next()
                            if (districtCode != null) {
                                put(districtCode, (get(districtCode) ?: 0) + count.visitCount)
                            }
                        }
                    }
                }.flowOn(Dispatchers.Default)
    }
