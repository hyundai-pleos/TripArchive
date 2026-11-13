package com.triparchive.presentation.home

import androidx.lifecycle.ViewModel
import com.triparchive.data.record.NewTripRecord
import com.triparchive.data.record.RecordRepository
import com.triparchive.domain.navigation.Destination
import com.triparchive.domain.navigation.DestinationSource
import com.triparchive.domain.navigation.ResolveDestinationCategory
import com.triparchive.domain.record.TripStopDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject

/** A trip belongs to one Home navigation entry and remains available while visiting the map. */
@HiltViewModel
internal class TripSessionViewModel
    @Inject
    constructor(
        private val repository: RecordRepository,
        private val destinationSource: DestinationSource,
        private val resolveCategory: ResolveDestinationCategory,
    ) : ViewModel() {
        private val startedAt = System.currentTimeMillis()
        val destination = destinationSource.observe()
        private val arrivals = linkedMapOf<Long, TripStopDraft>()
        private val resolvedStops = mutableMapOf<Long, TripStopDraft>()
        private val saveMutex = Mutex()
        private var saved = false

        private fun stop(destination: Destination): TripStopDraft? {
            val name = destination.name.trim().takeIf(String::isNotEmpty) ?: return null
            val existing = resolvedStops[destination.id]
            if (existing != null &&
                (destination.category == null || existing.category == destination.category)
            ) {
                return existing
            }
            return TripStopDraft(
                name,
                resolveCategory(destination.category),
            ).also { resolvedStops[destination.id] = it }
        }

        fun recordArrival(destination: Destination) {
            if (saved) return
            if (destination.id !in arrivals) stop(destination)?.let { arrivals[destination.id] = it }
            destinationSource.acknowledgeArrival(destination.id)
        }

        fun createDemoArrival(destination: Destination): Destination = destinationSource.createDemoArrival(destination)

        suspend fun save(
            profileId: Long,
            pendingArrival: Destination?,
        ): Boolean {
            if (!saveMutex.tryLock()) return false
            try {
                if (saved) return true
                val pending =
                    (destinationSource.pendingArrivals() + listOfNotNull(pendingArrival))
                        .distinctBy { it.id }
                        .filterNot { it.id in arrivals }
                val stops = arrivals.values.toList() + pending.mapNotNull(::stop)
                if (stops.isEmpty()) {
                    saved = true
                    return true
                }
                val title =
                    if (stops.size ==
                        1
                    ) {
                        "${stops.first().placeName} 여행"
                    } else {
                        "${stops.first().placeName} 외 ${stops.size - 1}곳 여행"
                    }
                repository.saveCompletedRecord(
                    profileId,
                    NewTripRecord(
                        title = title,
                        startedAt = startedAt,
                        endedAt = System.currentTimeMillis(),
                        distanceMeters = null,
                        stops = stops,
                    ),
                )
                saved = true
                pending.forEach { destinationSource.acknowledgeArrival(it.id) }
                return true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return false
            } finally {
                saveMutex.unlock()
            }
        }
    }
