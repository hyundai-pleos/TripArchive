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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class PleosDestinationSource(
    private val context: Context,
) {
    fun observe() =
        callbackFlow {
            val handler = Handler(Looper.getMainLooper())
            val helper = NaviHelper(context)
            var state = DestinationState()
            var closed = false
            var arrivedName: String? = null

            fun post(action: () -> Unit) {
                handler.post {
                    if (!closed) {
                        action()
                        trySend(state)
                    }
                }
            }

            fun refresh() {
                runCatching {
                    helper.getRouteStateInfo()
                    helper.getDestinationInfo()
                }.onFailure { Log.w(TAG, "Unable to refresh navigation state", it) }
            }
            val listener =
                object : NaviHelperEventListener {
                    override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) =
                        post {
                            if (routeStateInfo.naviMode == NaviMode.ROUTE_GUIDING ||
                                routeStateInfo.naviMode == NaviMode.SIMULATION_GUIDING
                            ) {
                                state = state.guidanceObserved()
                            }
                        }

                    override fun onDestinationInfo(destinationInfo: DestinationInfo) =
                        post {
                            val name = destinationInfo.name
                            if (name.isNotBlank() && name != arrivedName && !state.cancelled) {
                                // Category classification is supplied by a separate provider in the future.
                                state =
                                    state.destinationChanged(
                                        name,
                                        category = null,
                                        destinationId =
                                            destinationIdentity(
                                                name = name,
                                                poiId = destinationInfo.poiId,
                                                poiSubId = destinationInfo.poiSubId,
                                                poiProvider = destinationInfo.poiProvider,
                                                latitude = destinationInfo.latitude,
                                                longitude = destinationInfo.longitude,
                                            ),
                                    )
                            }
                        }

                    override fun onError(errorCode: NaviErrorCode) {
                        Log.w(TAG, "Navigation error: $errorCode")
                    }

                    override fun onDestinationChanged() {
                        refresh()
                    }

                    override fun onRoutingCompleted() {
                        post {
                            arrivedName = null
                            state = state.routingCompleted()
                        }
                        refresh()
                    }

                    override fun onReRouteEnded() {
                        refresh()
                    }

                    override fun onRouteCancelled() = post { state = state.routeChanged(false) }

                    override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) =
                        post {
                            val name = destinationArrivedInfo.name
                            if (name.isNotBlank() && name != arrivedName && !state.cancelled) {
                                arrivedName = name
                                state = state.arrive(name)
                            }
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
                closed = true
                handler.removeCallbacksAndMessages(null)
                helper.removeListener(listener)
                helper.release()
            }
        }
}

private const val TAG = "PleosDestinationSource"
