package com.triparchive.domain.navigation

/** Current route and the next arrival awaiting confirmation are independent visits. */
data class DestinationState(
    val current: Destination? = null,
    val pendingArrival: Destination? = null,
    val routing: Boolean = false,
)
