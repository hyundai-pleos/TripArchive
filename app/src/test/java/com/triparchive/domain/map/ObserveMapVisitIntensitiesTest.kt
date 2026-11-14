package com.triparchive.domain.map

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveMapVisitIntensitiesTest {
    @Test
    fun `districts have five levels and provinces use the total of their districts`() =
        runBlocking {
            val counts =
                mapOf(
                    "11110" to 1,
                    "11140" to 2,
                    "26110" to 3,
                    "41111" to 4,
                    "51150" to 5,
                    "51210" to Int.MAX_VALUE,
                    "50110" to 0,
                    "50130" to -1,
                    "11999" to 100,
                )
            val observe = ObserveMapVisitIntensities { flowOf(counts) }

            assertEquals(
                listOf(
                    mapOf(
                        "11" to VisitIntensity.THIRD,
                        "11110" to VisitIntensity.FIRST,
                        "11140" to VisitIntensity.SECOND,
                        "26" to VisitIntensity.THIRD,
                        "26110" to VisitIntensity.THIRD,
                        "41" to VisitIntensity.FOURTH,
                        "41111" to VisitIntensity.FOURTH,
                        "51" to VisitIntensity.FIFTH,
                        "51150" to VisitIntensity.FIFTH,
                        "51210" to VisitIntensity.FIFTH,
                    ),
                ),
                observe(boundaries, 1L).toList(),
            )
        }

    @Test
    fun `province sums visits across three districts while each district keeps its own level`() =
        runBlocking {
            val observe = ObserveMapVisitIntensities { flowOf(mapOf("11110" to 1, "11140" to 2, "11170" to 1)) }

            assertEquals(
                mapOf(
                    "11" to VisitIntensity.FOURTH,
                    "11110" to VisitIntensity.FIRST,
                    "11140" to VisitIntensity.SECOND,
                    "11170" to VisitIntensity.FIRST,
                ),
                observe(boundaries, 1L).toList().single(),
            )
        }

    @Test
    fun `updates change both levels and deleting visits clears them`() =
        runBlocking {
            val observe =
                ObserveMapVisitIntensities {
                    flowOf(emptyMap(), mapOf("11110" to 5), mapOf("11110" to 6), emptyMap())
                }

            assertEquals(
                listOf(emptyMap(), mapOf("11" to VisitIntensity.FIFTH, "11110" to VisitIntensity.FIFTH), emptyMap()),
                observe(boundaries, 1L).toList(),
            )
        }

    @Test
    fun `selected profile id is passed to the visit source`() =
        runBlocking {
            var requestedId = -1L
            val observe =
                ObserveMapVisitIntensities { profileId ->
                    requestedId = profileId
                    flowOf(mapOf("11140" to 1))
                }

            assertEquals(
                mapOf("11" to VisitIntensity.FIRST, "11140" to VisitIntensity.FIRST),
                observe(boundaries, 7L).toList().single(),
            )
            assertEquals(7L, requestedId)
        }

    private val boundaries =
        AdministrativeBoundaries(
            provinces = listOf("11", "26", "41", "50", "51").map(::boundary),
            districts =
                listOf(
                    "11110",
                    "11140",
                    "11170",
                    "26110",
                    "41111",
                    "50110",
                    "50130",
                    "51150",
                    "51210",
                ).map(::boundary),
        )

    private fun boundary(code: String) = AdministrativeBoundary(code, code, emptyList())
}
