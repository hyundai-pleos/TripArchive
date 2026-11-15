package com.triparchive.domain.record

import com.triparchive.domain.navigation.DestinationCategory

data class TripStopDraft(
    val placeName: String,
    val category: DestinationCategory? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
