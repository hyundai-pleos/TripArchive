package com.triparchive.data.navigation

/** A source-provided destination type. Names are never classified in the UI. */
enum class DestinationCategory(
    val label: String,
    val assetFileName: String,
) {
    MART("마트", "mart.glb"),
    SCHOOL("학교", "school.glb"),
    TOURISM("관광", "tourism.glb"),
    RESTAURANT("음식점", "restaurant.glb"),
    HOSPITAL("병원", "hospital.glb"),
}

/** State kept only for the current foreground profile session. */
data class DestinationState(
    val name: String? = null,
    val arrived: String? = null,
    val routing: Boolean = false,
    val category: DestinationCategory? = null,
    val arrivedCategory: DestinationCategory? = null,
    val destinationId: String? = null,
    val arrivedDestinationId: String? = null,
    val cancelled: Boolean = false,
) {
    fun routeChanged(active: Boolean): DestinationState =
        copy(
            routing = active,
            name = if (active) name else null,
            category = if (active) category else null,
            destinationId = if (active) destinationId else null,
            cancelled = !active,
        )

    fun destinationChanged(
        value: String?,
        category: DestinationCategory? = null,
        destinationId: String? = null,
    ): DestinationState {
        val validName = value?.takeIf { it.isNotBlank() }
        return copy(
            name = validName,
            category = category.takeIf { validName != null },
            destinationId = validName?.let { destinationId ?: destinationIdentity(it) },
            cancelled = if (validName != null) false else cancelled,
        )
    }

    fun arrive(
        value: String,
        category: DestinationCategory? = this.category.takeIf { name == value },
    ): DestinationState =
        copy(
            name = null,
            arrived = value,
            routing = false,
            category = null,
            arrivedCategory = category,
            destinationId = null,
            arrivedDestinationId = destinationId.takeIf { name == value },
            cancelled = false,
        )

    fun acknowledge(): DestinationState = copy(arrived = null, arrivedCategory = null, arrivedDestinationId = null)

    /** Ignore late guidance callbacks from a route that was explicitly cancelled. */
    internal fun guidanceObserved(): DestinationState = if (cancelled) this else routeChanged(true)

    /** Route calculation completion permits guidance again and retires any prior arrival. */
    internal fun routingCompleted(): DestinationState = acknowledge().copy(cancelled = false)
}

/** Stable across refreshes; use the provider's POI identity when available. */
internal fun destinationIdentity(
    name: String,
    poiId: String = "",
    poiSubId: String? = null,
    poiProvider: String = "",
    latitude: Double? = null,
    longitude: Double? = null,
): String {
    fun part(value: String): String = "${value.length}:$value"

    val id = poiId.trim()
    if (id.isNotEmpty()) {
        return "poi:${part(poiProvider.trim())}:${part(id)}:${part(poiSubId?.trim().orEmpty())}"
    }
    if (latitude != null && longitude != null && latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0 && (latitude != 0.0 || longitude != 0.0)
    ) {
        return "coordinates:$latitude:$longitude:${part(name)}"
    }
    return "name:${part(name)}"
}
