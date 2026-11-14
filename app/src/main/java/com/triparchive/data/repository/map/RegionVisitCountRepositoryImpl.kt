package com.triparchive.data.repository.map

import com.triparchive.data.record.RecordDao
import com.triparchive.domain.map.RegionVisitCountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RegionVisitCountRepositoryImpl
    @Inject
    constructor(
        private val recordDao: RecordDao,
    ) : RegionVisitCountRepository {
        override fun observeDistrictVisitCounts(profileId: Long): Flow<Map<String, Int>> =
            recordDao.observeDistrictVisitCounts(profileId).map { counts ->
                counts.associate { it.districtCode to it.visitCount }
            }
    }
