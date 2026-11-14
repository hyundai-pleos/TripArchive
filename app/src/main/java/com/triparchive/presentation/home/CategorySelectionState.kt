package com.triparchive.presentation.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.triparchive.domain.navigation.CategoryLookupStatus
import com.triparchive.domain.navigation.Destination
import com.triparchive.domain.navigation.DestinationCategory
import com.triparchive.domain.navigation.DestinationState
import com.triparchive.presentation.home.components.CategoryChoice

/** Retains a manual decision for each visit for the lifetime of the home trip. */
internal class CategorySelectionState {
    private val decisions = mutableStateMapOf<Long, DestinationCategory?>()
    private var currentId by mutableStateOf<Long?>(null)

    var pending by mutableStateOf(false)
        private set
    var selected by mutableStateOf<CategoryChoice?>(null)
        private set
    var remainingMillis by mutableIntStateOf(5000)
        private set

    val secondsRemaining: Int get() = (remainingMillis + 999) / 1000

    fun update(state: DestinationState) {
        val current = state.current
        if (current?.id != currentId) {
            currentId = current?.id
            pending = false
            selected = null
            remainingMillis = 5000
        }
        val awaitingChoice =
            current != null && current.id !in decisions && current.category == null &&
                (
                    current.categoryStatus == CategoryLookupStatus.SUCCESS ||
                        current.categoryStatus == CategoryLookupStatus.FAILURE
                )
        if (awaitingChoice) {
            pending = true
        } else if (pending) {
            pending = false
            selected = null
            remainingMillis = 5000
        }
    }

    fun isPendingFor(destination: Destination?): Boolean =
        pending && destination != null && destination.id == currentId && destination.category == null &&
            (
                destination.categoryStatus == CategoryLookupStatus.SUCCESS ||
                    destination.categoryStatus == CategoryLookupStatus.FAILURE
            )

    fun hasDecision(destinationId: Long): Boolean = destinationId in decisions

    fun apply(destination: Destination): Destination =
        if (destination.id in decisions) {
            destination.copy(
                category = decisions[destination.id],
                categoryStatus = CategoryLookupStatus.SUCCESS,
                categoryFailure = null,
            )
        } else {
            destination
        }

    fun copyDecision(
        from: Destination,
        to: Destination,
    ) {
        if (from.id in decisions) decisions[to.id] = decisions[from.id]
    }

    fun select(choice: CategoryChoice) {
        if (pending) selected = choice
    }

    fun confirm() {
        val id = currentId ?: return
        if (!pending) return
        decisions[id] = selected?.category
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
        decisions.clear()
        currentId = null
        pending = false
        selected = null
        remainingMillis = 5000
    }
}
