package com.triparchive.domain.map

import kotlinx.coroutines.flow.Flow

fun interface RegionVisitCountRepository {
    /** Emits the current totals and subsequent changes, keyed by the boundary dataset's 시·군·구 code. */
    fun observeDistrictVisitCounts(districts: List<AdministrativeBoundary>): Flow<Map<String, Int>>
}
