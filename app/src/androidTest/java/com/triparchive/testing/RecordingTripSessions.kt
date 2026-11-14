package com.triparchive.testing

import com.triparchive.data.profile.Profile
import com.triparchive.data.trip.TripSession
import com.triparchive.data.trip.TripSessionRepository
import com.triparchive.domain.navigation.DestinationCategory
import kotlinx.coroutines.flow.flowOf

internal class RecordingTripSessions : TripSessionRepository {
    var beforeAcquire: suspend () -> Unit = {}
    val acquisitions = mutableListOf<Triple<Long, String, DestinationCategory>>()

    override suspend fun acquireProp(
        profileId: Long,
        sessionId: String,
        category: DestinationCategory,
    ): Boolean {
        beforeAcquire()
        acquisitions += Triple(profileId, sessionId, category)
        return true
    }

    override suspend fun openOrResume(profile: Profile): TripSession = error("Not used by this test")

    override suspend fun finish(
        profileId: Long,
        sessionId: String,
    ): TripSession? = null

    override fun observe(profileId: Long) = flowOf<TripSession?>(null)
}
