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
    private var currentKey: DestinationKey? = null
    private var lastDestinationKey: DestinationKey? = null
    private var lastArrivalKey: DestinationKey? = null
    private val waitingArrivals = mutableListOf<Destination>()

    fun routeChanged(active: Boolean) {
        mutableStates.value =
            state.copy(
                routing = active,
                confirmedDestinations = if (active) confirmedWith(state.current) else state.confirmedDestinations,
            )
    }

    fun destinationSelectionChanged() {
        if (state.current == null) {
            lastDestination = null
            lastDestinationKey = null
        }
        lastArrival = null
        lastArrivalKey = null
    }

    fun routeStarted() {
        destinationSelectionChanged()
        routeChanged(true)
    }

    fun destinationChanged(
        name: String,
        latitude: Double,
        longitude: Double,
        poiId: String? = null,
        poiProvider: String? = null,
        poiSubId: String? = null,
    ) {
        if (name.isBlank()) {
            clearCurrent()
            return
        }

        val key = destinationKey(name, latitude, longitude, poiId, poiProvider, poiSubId)

        fun matches(
            destination: Destination?,
            previousKey: DestinationKey?,
        ): Boolean {
            if (destination == null) return false
            if (previousKey == key) return true
            if (previousKey !is DestinationKey.Coordinates && key !is DestinationKey.Coordinates) return false
            return destination.name == name && destination.latitude == latitude && destination.longitude == longitude
        }

        if (matches(state.current, currentKey)) {
            // Retain a known POI key when a refresh omits it; upgrade a coordinate key when available.
            if (key is DestinationKey.Poi) {
                currentKey = key
                lastDestinationKey = key
            }
            val current = state.current!!
            val updated = current.copy(name = name, latitude = latitude, longitude = longitude)
            if (updated != current) {
                lastDestination = updated
                mutableStates.value = state.copy(current = updated)
            }
            return
        }
        // Ignore SDK polling of the route just completed until a new route starts.
        if (state.current == null && matches(lastArrival, lastArrivalKey)) return
        val destination =
            Destination(++nextId, name, latitude, longitude, categoryStatus = CategoryLookupStatus.LOADING)
        lastDestination = destination
        currentKey = key
        lastDestinationKey = key
        mutableStates.value =
            state.copy(
                current = destination,
                confirmedDestinations = if (state.routing) confirmedWith(destination) else state.confirmedDestinations,
            )
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
        mutableStates.value =
            state.copy(
                current = current,
                pendingArrival = pending,
                confirmedDestinations = state.confirmedDestinations.map { it.updated() },
            )
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
        lastArrivalKey =
            when (arrived.id) {
                state.current?.id -> currentKey
                lastDestination?.id -> lastDestinationKey
                else -> null
            }
        if (state.current?.id == arrived.id) currentKey = null
        if (state.pendingArrival != null) waitingArrivals.add(arrived)
        mutableStates.value =
            state.copy(
                current = state.current?.takeUnless { it.id == arrived.id },
                pendingArrival = state.pendingArrival ?: arrived,
                confirmedDestinations = confirmedWith(arrived),
                routing = if (current != null || state.current == null) false else state.routing,
            )
    }

    fun acknowledgeArrival(id: Long) {
        if (state.pendingArrival?.id != id) return
        mutableStates.value = state.copy(pendingArrival = waitingArrivals.removeFirstOrNull())
    }

    fun clearCurrent() {
        currentKey = null
        mutableStates.value = state.copy(current = null, routing = false)
    }

    fun pendingArrivals(): List<Destination> = listOfNotNull(state.pendingArrival) + waitingArrivals

    fun lookupDestinations(): List<Destination> =
        (listOfNotNull(state.current, state.pendingArrival) + waitingArrivals + state.confirmedDestinations)
            .distinctBy { it.id }
            .filter { it.categoryStatus == CategoryLookupStatus.LOADING }

    private fun confirmedWith(destination: Destination?): List<Destination> =
        if (destination == null) {
            state.confirmedDestinations
        } else {
            state.confirmedDestinations.filterNot { it.id == destination.id } + destination
        }

    fun createDemoArrival(destination: Destination): Destination =
        destination.copy(
            id = ++nextId,
            category = null,
            categoryStatus = CategoryLookupStatus.IDLE,
            categoryFailure = null,
        )
}

private sealed interface DestinationKey {
    data class Poi(
        val provider: String,
        val id: String,
        val subId: String,
    ) : DestinationKey

    data class Coordinates(
        val name: String,
        val latitude: Double,
        val longitude: Double,
    ) : DestinationKey
}

private fun destinationKey(
    name: String,
    latitude: Double,
    longitude: Double,
    poiId: String?,
    poiProvider: String?,
    poiSubId: String?,
): DestinationKey {
    val id = poiId?.trim().orEmpty()
    return if (id.isNotEmpty()) {
        DestinationKey.Poi(poiProvider?.trim().orEmpty(), id, poiSubId?.trim().orEmpty())
    } else {
        DestinationKey.Coordinates(name, latitude, longitude)
    }
}
