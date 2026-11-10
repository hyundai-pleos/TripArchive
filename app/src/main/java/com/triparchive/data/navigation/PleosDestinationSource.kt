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
                }.onFailure { post { state = DestinationState() } }
            }
            val listener =
                object : NaviHelperEventListener {
                    override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) =
                        post {
                            state =
                                state.routeChanged(
                                    routeStateInfo.naviMode == NaviMode.ROUTE_GUIDING ||
                                        routeStateInfo.naviMode == NaviMode.SIMULATION_GUIDING,
                                )
                        }

                    override fun onDestinationInfo(destinationInfo: DestinationInfo) =
                        post {
                            val name = destinationInfo.name
                            if (name != arrivedName) state = state.destinationChanged(name)
                        }

                    override fun onError(errorCode: NaviErrorCode) = post { state = DestinationState() }

                    override fun onDestinationChanged() {
                        refresh()
                    }

                    override fun onRoutingCompleted() {
                        post {
                            arrivedName = null
                            state = DestinationState()
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
                            if (name.isNotBlank() && name != arrivedName) {
                                arrivedName = name
                                state = state.arrive(name)
                            }
                        }
                }
            helper.addListener(listener)
            runCatching {
                helper.initialize(
                    onConnected = ::refresh,
                    onFailed = { post { state = DestinationState() } },
                )
            }.onFailure { post { state = DestinationState() } }
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
