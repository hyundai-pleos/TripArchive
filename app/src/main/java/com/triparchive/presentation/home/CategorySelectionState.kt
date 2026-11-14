package com.triparchive.presentation.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.triparchive.data.navigation.DestinationCategory
import com.triparchive.data.navigation.DestinationState
import com.triparchive.presentation.home.components.CategoryChoice

/** Selection belongs to one Home entry, not to a particular composition of that entry. */
internal class CategorySelectionState {
    private var destinationId: String? = null
    private var destinationName: String? = null
    private var manuallyResolved = false
    private var resolvedCategory: DestinationCategory? = null
    private var arrivedName: String? = null
    private var arrivedId: String? = null
    private var arrivedWasManual = false
    private var arrivedManualCategory: DestinationCategory? = null

    var pending by mutableStateOf(false)
        private set
    var selected by mutableStateOf<CategoryChoice?>(null)
        private set
    var remainingMillis by mutableIntStateOf(5000)
        private set

    val secondsRemaining: Int get() = (remainingMillis + 999) / 1000

    fun update(state: DestinationState) {
        if (state.cancelled) {
            clearActive()
            return
        }
        val arrival = state.arrived
        if (arrival != null) {
            val matches =
                destinationId != null &&
                    (state.arrivedDestinationId?.let { it == destinationId } ?: (arrival == destinationName))
            if (arrivedName != arrival || arrivedId != state.arrivedDestinationId) {
                arrivedName = arrival
                arrivedId = state.arrivedDestinationId
                arrivedWasManual = matches && manuallyResolved
                arrivedManualCategory = resolvedCategory.takeIf { arrivedWasManual }
            }
            clearActive()
            return
        }
        if (!state.routing || state.name.isNullOrBlank()) return
        val id = state.destinationId ?: "name:${state.name}"
        if (id != destinationId) {
            clearActive()
            destinationId = id
            destinationName = state.name
            pending = state.category == null
        } else {
            destinationName = state.name
            if (!manuallyResolved) {
                if (state.category != null) {
                    pending = false
                    selected = null
                    remainingMillis = 5000
                } else if (!pending) {
                    pending = true
                }
            }
        }
        arrivedName = null
        arrivedId = null
        arrivedWasManual = false
        arrivedManualCategory = null
    }

    fun categoryFor(state: DestinationState): DestinationCategory? {
        val id = state.destinationId ?: state.name?.let { "name:$it" }
        return if (id != null && id == destinationId && manuallyResolved) resolvedCategory else state.category
    }

    fun arrivalCategoryFor(
        state: DestinationState,
        name: String,
    ): DestinationCategory? {
        val matchesActive =
            manuallyResolved && destinationName == name &&
                (state.arrivedDestinationId == null || state.arrivedDestinationId == destinationId)
        return when {
            matchesActive -> resolvedCategory
            arrivedName == name && arrivedWasManual -> arrivedManualCategory
            else -> state.arrivedCategory
        }
    }

    fun select(choice: CategoryChoice) {
        if (pending) selected = choice
    }

    fun confirm() {
        if (!pending) return
        manuallyResolved = true
        resolvedCategory = selected?.category
        pending = false
    }

    fun dismiss() {
        if (!pending) return
        selected = CategoryChoice.OTHER
        confirm()
    }

    fun tick() {
        if (!pending) return
        remainingMillis = (remainingMillis - 100).coerceAtLeast(0)
        if (remainingMillis == 0) confirm()
    }

    fun clear() {
        clearActive()
        arrivedName = null
        arrivedId = null
        arrivedWasManual = false
        arrivedManualCategory = null
    }

    private fun clearActive() {
        destinationId = null
        destinationName = null
        manuallyResolved = false
        resolvedCategory = null
        pending = false
        selected = null
        remainingMillis = 5000
    }
}
