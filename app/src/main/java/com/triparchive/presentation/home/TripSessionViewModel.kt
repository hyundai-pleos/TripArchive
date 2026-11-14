package com.triparchive.presentation.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triparchive.data.navigation.DestinationState
import com.triparchive.data.navigation.PleosDestinationSource
import com.triparchive.data.record.NewTripRecord
import com.triparchive.data.record.RecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject

/** A trip belongs to one Home navigation entry and remains available while visiting the map. */
@HiltViewModel
internal class TripSessionViewModel
    @Inject
    constructor(
        private val repository: RecordRepository,
    ) : ViewModel() {
        val categorySelection = CategorySelectionState()
        private val observedDestination = MutableStateFlow(DestinationState())
        private var observation: Job? = null
        private var demoOverride = false

        fun observeDestination(context: Context): StateFlow<DestinationState> {
            if (observation == null) {
                observation =
                    viewModelScope.launch {
                        PleosDestinationSource(context.applicationContext).observe().collect { state ->
                            if (!demoOverride) categorySelection.update(state)
                            observedDestination.value = state
                        }
                    }
            }
            return observedDestination
        }

        fun setDemoOverride(enabled: Boolean) {
            demoOverride = enabled
            if (!enabled) categorySelection.update(observedDestination.value)
        }

        private val startedAt = System.currentTimeMillis()
        private val arrivals = mutableListOf<String>()
        private val saveMutex = Mutex()
        private var saved = false

        fun recordArrival(place: String) {
            place.trim().takeIf(String::isNotEmpty)?.let(arrivals::add)
        }

        suspend fun save(
            profileId: Long,
            pendingArrival: String?,
        ): Boolean {
            if (!saveMutex.tryLock()) return false
            try {
                if (saved) return true
                val stops = arrivals.toList() + listOfNotNull(pendingArrival?.trim()?.takeIf(String::isNotEmpty))
                if (stops.isEmpty()) {
                    saved = true
                    return true
                }
                val title = if (stops.size == 1) "${stops.first()} 여행" else "${stops.first()} 외 ${stops.size - 1}곳 여행"
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
