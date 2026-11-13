package com.triparchive.data.record

import com.triparchive.domain.record.TripStopDraft

/** Explicitly invoked development data. Never called by application startup. */
object RecordFixtures {
    suspend fun insert(
        repository: RecordRepository,
        profileId: Long,
    ) {
        repository.saveCompletedRecord(
            profileId,
            NewTripRecord(
                title = "강릉·속초 여행",
                startedAt = 1793491200000,
                endedAt = 1793664000000,
                distanceMeters = null,
                stops = listOf("서울시청", "강릉 안목해변", "속초 중앙시장").map { TripStopDraft(it) },
            ),
        )
        repository.saveCompletedRecord(
            profileId,
            NewTripRecord(
                title = "부산 여행",
                startedAt = 1790812800000,
                endedAt = 1790985600000,
                distanceMeters = null,
                stops = listOf("서울 성동구", "대전 유성구", "부산 해운대구").map { TripStopDraft(it) },
            ),
        )
    }
}
