package com.triparchive.data.repository.map

import com.triparchive.domain.map.RegionVisitCountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class RegionVisitCountRepositoryImpl
    @Inject
    constructor() : RegionVisitCountRepository {
        // The visit history source is not connected yet.
        override fun observeDistrictVisitCounts(): Flow<Map<String, Int>> = flowOf(emptyMap())
    }
