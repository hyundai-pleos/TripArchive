package com.triparchive.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triparchive.data.record.NewTripRecord
import com.triparchive.data.record.RecordRepository
import com.triparchive.data.trip.TripSessionRepository
import com.triparchive.domain.navigation.CategoryLookupStatus
import com.triparchive.domain.navigation.Destination
import com.triparchive.domain.navigation.DestinationCategory
import com.triparchive.domain.navigation.DestinationSource
import com.triparchive.domain.navigation.DestinationState
import com.triparchive.domain.navigation.ResolveDestinationCategory
import com.triparchive.domain.record.TripStopDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
        private val tripSessions: TripSessionRepository,
    ) : ViewModel() {
        val categorySelection = CategorySelectionState()
        private val startedAt = System.currentTimeMillis()
        private val observedDestination = MutableStateFlow(DestinationState())
        val destination = observedDestination.asStateFlow()
        private var observation: Job? = null
        private val arrivals = linkedMapOf<Long, TripStopDraft>()
        private val resolvedStops = mutableMapOf<Long, TripStopDraft>()
        private val saveMutex = Mutex()
        private var saved = false
        private val acquired = mutableSetOf<Pair<String, DestinationCategory>>()

        fun startDestinationObservation(
            scope: CoroutineScope = viewModelScope,
            profileId: Long? = null,
            sessionId: String? = null,
        ) {
            if (observation != null) return
            observation =
                scope.launch {
                    destinationSource.observe().collect { state ->
                        observedDestination.value = state
                        if (profileId != null && sessionId != null) {
                            val confirmed =
                                state.confirmedDestinations +
                                    listOfNotNull(
                                        state.current?.takeIf { state.routing },
                                        state.pendingArrival,
                                    )
                            confirmed.distinctBy { it.id }.forEach {
                                acquireDestinationProp(profileId, sessionId, categorySelection.apply(it))
                            }
                        }
                    }
                }
        }

        fun confirmDestination(
            profileId: Long,
            sessionId: String,
            destination: Destination,
            scope: CoroutineScope = viewModelScope,
        ) {
            // A map/profile transition must not cancel a confirmed manual choice's write.
            scope.launch { acquireDestinationProp(profileId, sessionId, destination) }
        }

        suspend fun acquireDestinationProp(
            profileId: Long,
            sessionId: String,
            destination: Destination,
        ) {
            val resolved = categorySelection.apply(destination)
            val category = resolved.category ?: return
            if (resolved.categoryStatus != CategoryLookupStatus.SUCCESS) return
            val key = sessionId to category
            if (key in acquired) return
            // Keep the originating session ID across suspension/retries. The repository rejects
            // a late response after finish instead of awarding it to the next trip.
            while (true) {
                try {
                    if (tripSessions.acquireProp(profileId, sessionId, category)) acquired += key
                    return
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    delay(1000)
                }
            }
        }

        private fun stop(destination: Destination): TripStopDraft? {
            val chosen = categorySelection.apply(destination)
            val name = chosen.name.trim().takeIf(String::isNotEmpty) ?: return null
            val existing = resolvedStops[chosen.id]
            val category =
                when {
                    categorySelection.hasDecision(chosen.id) -> chosen.category
                    chosen.category != null -> chosen.category
                    existing != null -> existing.category
                    else -> resolveCategory(null)
                }
            if (existing != null && existing.category == category && existing.placeName == name) return existing
            return TripStopDraft(name, category).also { resolvedStops[chosen.id] = it }
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
