package com.triparchive

import com.triparchive.domain.navigation.CategoryLookupStatus
import com.triparchive.domain.navigation.Destination
import com.triparchive.domain.navigation.DestinationCategory
import com.triparchive.domain.navigation.DestinationSource
import com.triparchive.domain.navigation.DestinationState
import kotlinx.coroutines.flow.MutableStateFlow

internal object DestinationTestFixtures {
    fun place(
        name: String,
        id: Long = 1,
        category: DestinationCategory? = null,
        status: CategoryLookupStatus = CategoryLookupStatus.IDLE,
    ) = Destination(id, name, 37.0, 127.0, category, status)

    fun active(
        name: String,
        id: Long = 1,
        categoryStatus: CategoryLookupStatus = CategoryLookupStatus.IDLE,
    ) = DestinationState(current = place(name, id, status = categoryStatus), routing = true)

    fun arrived(
        name: String,
        category: DestinationCategory? = null,
        categoryStatus: CategoryLookupStatus = CategoryLookupStatus.IDLE,
    ) = DestinationState(pendingArrival = place(name, category = category, status = categoryStatus))
}

internal class FakeDestinationSource(
    private val states: MutableStateFlow<DestinationState>,
) : DestinationSource {
    private var nextDemoId = 1000L

    override fun observe() = states

    override fun pendingArrivals() = listOfNotNull(states.value.pendingArrival)

    override fun acknowledgeArrival(id: Long) {
        if (states.value.pendingArrival?.id == id) states.value = states.value.copy(pendingArrival = null)
    }

    override fun createDemoArrival(destination: Destination) = destination.copy(id = ++nextDemoId, category = null)
}
