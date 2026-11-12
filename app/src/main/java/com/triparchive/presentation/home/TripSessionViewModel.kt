package com.triparchive.presentation.home

import androidx.lifecycle.ViewModel
import com.triparchive.data.record.NewTripRecord
import com.triparchive.data.record.RecordRepository
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
    ) : ViewModel() {
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
