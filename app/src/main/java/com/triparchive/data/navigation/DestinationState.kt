package com.triparchive.data.navigation

/** State kept only for the current foreground profile session. */
data class DestinationState(
    val name: String? = null,
    val arrived: String? = null,
    val routing: Boolean = false,
) {
    fun routeChanged(active: Boolean): DestinationState = copy(routing = active, name = if (active) name else null)

    fun destinationChanged(value: String?): DestinationState = copy(name = value?.takeIf { it.isNotBlank() })

    fun arrive(value: String): DestinationState = copy(name = null, arrived = value, routing = false)

    fun acknowledge(): DestinationState = copy(arrived = null)
}
