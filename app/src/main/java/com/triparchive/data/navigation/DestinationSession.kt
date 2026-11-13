package com.triparchive.data.navigation

import com.triparchive.domain.navigation.CategoryLookupResult
import com.triparchive.domain.navigation.CategoryLookupStatus
import com.triparchive.domain.navigation.Destination
import com.triparchive.domain.navigation.DestinationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** All mutations run on the main thread. This session outlives SDK observation. */
internal class DestinationSession {
    private val mutableStates = MutableStateFlow(DestinationState())
    val states = mutableStates.asStateFlow()
    val state: DestinationState get() = states.value
    private var nextId = 0L
    private var lastDestination: Destination? = null
    private var lastArrival: Destination? = null
    private val waitingArrivals = mutableListOf<Destination>()

    fun routeChanged(active: Boolean) {
        mutableStates.value = state.copy(routing = active)
    }

    fun destinationSelectionChanged() {
        if (state.current == null) lastDestination = null
        lastArrival = null
    }

    fun routeStarted() {
        destinationSelectionChanged()
        routeChanged(true)
    }

    fun destinationChanged(
        name: String,
        latitude: Double,
        longitude: Double,
    ) {
        if (name.isBlank()) {
            clearCurrent()
            return
        }

        fun Destination.matches() = this.name == name && this.latitude == latitude && this.longitude == longitude
        if (state.current?.matches() == true) return
        // Ignore SDK polling of the route just completed until a new route starts.
        if (state.current == null && lastArrival?.matches() == true) return
        val destination =
            Destination(++nextId, name, latitude, longitude, categoryStatus = CategoryLookupStatus.LOADING)
        lastDestination = destination
        mutableStates.value = state.copy(current = destination)
    }

    fun categoryResolved(
        id: Long,
        result: CategoryLookupResult,
    ) {
        fun Destination.updated() =
            if (this.id == id) {
                copy(
                    category = result.category,
                    categoryStatus = result.status,
                    categoryFailure = result.reason,
                )
            } else {
                this
            }
        waitingArrivals.indices.forEach { waitingArrivals[it] = waitingArrivals[it].updated() }
        val current = state.current?.updated()
        val pending = state.pendingArrival?.updated()
        if (current?.id == id) lastDestination = current
        mutableStates.value = state.copy(current = current, pendingArrival = pending)
    }

    fun arrive(name: String) {
        if (name.isBlank()) return
        val current = state.current?.takeIf { it.name == name }
        if (current == null &&
            (lastArrival?.name == name || state.pendingArrival?.name == name || waitingArrivals.any { it.name == name })
        ) {
            return
        }
        val arrived =
            current ?: lastDestination?.takeIf { it.name == name }
                ?: Destination(++nextId, name, 0.0, 0.0)
        lastArrival = arrived
        if (state.pendingArrival != null) waitingArrivals.add(arrived)
        mutableStates.value =
            state.copy(
                current = state.current?.takeUnless { it.id == arrived.id },
                pendingArrival = state.pendingArrival ?: arrived,
                routing = if (current != null || state.current == null) false else state.routing,
            )
    }

    fun acknowledgeArrival(id: Long) {
        if (state.pendingArrival?.id != id) return
        mutableStates.value = state.copy(pendingArrival = waitingArrivals.removeFirstOrNull())
    }

    fun clearCurrent() {
        mutableStates.value = state.copy(current = null, routing = false)
    }

    fun pendingArrivals(): List<Destination> = listOfNotNull(state.pendingArrival) + waitingArrivals

    fun lookupDestinations(): List<Destination> =
        (listOfNotNull(state.current, state.pendingArrival) + waitingArrivals)
            .filter { it.categoryStatus == CategoryLookupStatus.LOADING }

    fun createDemoArrival(destination: Destination): Destination =
        destination.copy(
            id = ++nextId,
            category = null,
            categoryStatus = CategoryLookupStatus.IDLE,
            categoryFailure = null,
        )
}
