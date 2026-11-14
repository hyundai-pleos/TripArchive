package com.triparchive.domain.record

import com.triparchive.domain.navigation.DestinationCategory

data class TripStopDraft(
    val placeName: String,
    val category: DestinationCategory? = null,
    // Coordinates are retained only while a trip is being recorded, never written to the database.
    val latitude: Double? = null,
    val longitude: Double? = null,
    val districtCode: String? = null,
)
