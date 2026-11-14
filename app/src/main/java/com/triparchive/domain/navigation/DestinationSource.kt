package com.triparchive.domain.navigation

import kotlinx.coroutines.flow.Flow

/** One instance belongs to one trip and retains visit IDs across observation restarts. */
interface DestinationSource {
    fun observe(): Flow<DestinationState>

    fun pendingArrivals(): List<Destination>

    fun acknowledgeArrival(id: Long)

    fun createDemoArrival(destination: Destination): Destination
}
