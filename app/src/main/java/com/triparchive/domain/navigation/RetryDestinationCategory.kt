package com.triparchive.domain.navigation

import kotlinx.coroutines.delay

/** Retry lookup failures without manufacturing a category or awarding a loading result. */
internal suspend fun DestinationCategoryClassifier.classifyWithRetry(
    destination: Destination,
    retryDelayMillis: Long = 1000,
): CategoryLookupResult {
    var result = classify(destination.name, destination.latitude, destination.longitude)
    repeat(2) { attempt ->
        if (result.status != CategoryLookupStatus.FAILURE) return result
        delay(retryDelayMillis * (attempt + 1))
        result = classify(destination.name, destination.latitude, destination.longitude)
    }
    return result
}
