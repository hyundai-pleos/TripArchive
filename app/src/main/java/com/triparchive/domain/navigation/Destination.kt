package com.triparchive.domain.navigation

/** One visit to a destination; revisiting the same place creates another ID. */
data class Destination(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val category: DestinationCategory? = null,
    val categoryStatus: CategoryLookupStatus = CategoryLookupStatus.IDLE,
    val categoryFailure: String? = null,
)
