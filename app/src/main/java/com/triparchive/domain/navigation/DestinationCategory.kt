package com.triparchive.domain.navigation

enum class DestinationCategory { MART, SCHOOL, TOURISM, FOOD, HOSPITAL }

enum class CategoryLookupStatus { IDLE, LOADING, SUCCESS, FAILURE }

data class CategoryLookupResult(
    val category: DestinationCategory?,
    val status: CategoryLookupStatus,
    val reason: String? = null,
)

fun interface DestinationCategoryClassifier {
    suspend fun classify(
        name: String,
        latitude: Double,
        longitude: Double,
    ): CategoryLookupResult
}
