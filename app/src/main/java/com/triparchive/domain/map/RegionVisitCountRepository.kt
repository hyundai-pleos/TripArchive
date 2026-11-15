package com.triparchive.domain.map

import kotlinx.coroutines.flow.Flow

fun interface RegionVisitCountRepository {
    /** Emits totals for the given profile, keyed by the boundary dataset's 시·군·구 code. */
    fun observeDistrictVisitCounts(profileId: Long): Flow<Map<String, Int>>
}
