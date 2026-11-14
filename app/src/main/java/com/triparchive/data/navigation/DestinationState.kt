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
) {
    fun routeChanged(active: Boolean): DestinationState =
        copy(
            routing = active,
            name = if (active) name else null,
            category = if (active) category else null,
        )

    fun destinationChanged(
        value: String?,
        category: DestinationCategory? = null,
    ): DestinationState {
        val validName = value?.takeIf { it.isNotBlank() }
        return copy(name = validName, category = category.takeIf { validName != null })
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
        )

    fun acknowledge(): DestinationState = copy(arrived = null, arrivedCategory = null)
}
