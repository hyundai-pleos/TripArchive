package com.triparchive.domain.navigation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RetryDestinationCategoryTest {
    private val destination = Destination(1, "장소", 37.0, 127.0)

    @Test
    fun retriesFailuresAndReturnsOnlyTheSuccessfulCategory() =
        runBlocking {
            var attempts = 0
            val classifier =
                DestinationCategoryClassifier { _, _, _ ->
                    attempts++
                    if (attempts < 3) {
                        CategoryLookupResult(null, CategoryLookupStatus.FAILURE)
                    } else {
                        CategoryLookupResult(DestinationCategory.FOOD, CategoryLookupStatus.SUCCESS)
                    }
                }
            assertEquals(DestinationCategory.FOOD, classifier.classifyWithRetry(destination, 0).category)
            assertEquals(3, attempts)
        }

    @Test
    fun exhaustedRetriesNeverManufactureACategory() =
        runBlocking {
            var attempts = 0
            val classifier =
                DestinationCategoryClassifier { _, _, _ ->
                    attempts++
                    CategoryLookupResult(null, CategoryLookupStatus.FAILURE)
                }
            val result = classifier.classifyWithRetry(destination, 0)
            assertEquals(3, attempts)
            assertEquals(CategoryLookupStatus.FAILURE, result.status)
            assertNull(result.category)
        }

    @Test(expected = CancellationException::class)
    fun cancellationDoesNotRetryOrProduceFallback() =
        runBlocking<Unit> {
            DestinationCategoryClassifier { _, _, _ -> throw CancellationException() }
                .classifyWithRetry(destination, 0)
        }
}
