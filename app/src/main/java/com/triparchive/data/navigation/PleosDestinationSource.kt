package com.triparchive.data.navigation

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.triparchive.domain.navigation.Destination
import com.triparchive.domain.navigation.DestinationCategoryClassifier
import com.triparchive.domain.navigation.DestinationSource
import com.triparchive.domain.navigation.classifyWithRetry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class PleosDestinationSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val classifier: DestinationCategoryClassifier,
    ) : DestinationSource {
        private val session = DestinationSession()

        override fun pendingArrivals(): List<Destination> = session.pendingArrivals()

        override fun acknowledgeArrival(id: Long) = session.acknowledgeArrival(id)

        override fun createDemoArrival(destination: Destination): Destination = session.createDemoArrival(destination)

        override fun observe() =
            callbackFlow {
                val handler = Handler(Looper.getMainLooper())
                val helper = NaviHelper(context)
                var closed = false
                var routeCancelled = false
                val categoryJobs = mutableMapOf<Long, Job>()

                fun post(action: () -> Unit) {
                    handler.post {
                        if (!closed) {
                            action()
                        }
                    }
                }

                fun refresh() {
                    runCatching {
                        helper.getRouteStateInfo()
                        helper.getDestinationInfo()
                    }.onFailure { Log.w(TAG, "Unable to refresh navigation state", it) }
                }

                fun syncLookups() {
                    val destinations = session.lookupDestinations().associateBy { it.id }
                    categoryJobs.keys.toList().filter { it !in destinations }.forEach { id ->
                        categoryJobs.remove(id)?.cancel()
                    }
                    destinations.forEach { (id, destination) ->
                        if (id !in categoryJobs) {
                            categoryJobs[id] =
                                launch {
                                    val result =
                                        classifier.classifyWithRetry(destination)
                                    post {
                                        categoryJobs.remove(id)
                                        session.categoryResolved(id, result)
                                    }
                                }
                        }
                    }
                }

                val stateJob =
                    launch {
                        session.states.collect {
                            syncLookups()
                            trySend(it)
                        }
                    }

                val listener =
                    object : NaviHelperEventListener {
                        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) =
                            post {
                                if (!routeCancelled) {
                                    session.routeChanged(
                                        routeStateInfo.naviMode == NaviMode.ROUTE_GUIDING ||
                                            routeStateInfo.naviMode == NaviMode.SIMULATION_GUIDING,
                                    )
                                }
                            }

                        override fun onDestinationInfo(destinationInfo: DestinationInfo) =
                            post {
                                if (!routeCancelled && destinationInfo.name.isNotBlank()) {
                                    session.destinationChanged(
                                        destinationInfo.name,
                                        destinationInfo.latitude,
                                        destinationInfo.longitude,
                                        poiId = destinationInfo.poiId,
                                        poiProvider = destinationInfo.poiProvider,
                                        poiSubId = destinationInfo.poiSubId,
                                    )
                                }
                            }

                        override fun onError(errorCode: NaviErrorCode) {
                            Log.w(TAG, "Navigation error: $errorCode")
                        }

                        override fun onDestinationChanged() {
                            post { session.destinationSelectionChanged() }
                            refresh()
                        }

                        override fun onRoutingCompleted() {
                            post {
                                routeCancelled = false
                                session.routeStarted()
                            }
                            refresh()
                        }

                        override fun onReRouteEnded() {
                            refresh()
                        }

                        override fun onRouteCancelled() =
                            post {
                                routeCancelled = true
                                session.clearCurrent()
                            }

                        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) =
                            post {
                                if (!routeCancelled) session.arrive(destinationArrivedInfo.name)
                            }
                    }
                helper.addListener(listener)
                runCatching {
                    helper.initialize(
                        onConnected = ::refresh,
                        onFailed = { Log.w(TAG, "Unable to connect to navigation") },
                    )
                }.onFailure { Log.w(TAG, "Unable to initialize navigation", it) }
                val refreshJob =
                    launch {
                        while (true) {
                            delay(5000)
                            refresh()
                        }
                    }
                awaitClose {
                    refreshJob.cancel()
                    stateJob.cancel()
                    categoryJobs.values.forEach { it.cancel() }
                    closed = true
                    handler.removeCallbacksAndMessages(null)
                    helper.removeListener(listener)
                    helper.release()
                }
            }
    }

private const val TAG = "PleosDestinationSource"
