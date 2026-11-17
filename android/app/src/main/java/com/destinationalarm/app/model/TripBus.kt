package com.destinationalarm.app.model

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-process bus between the tracking foreground service and the UI.
 * The service is the single source of truth while a trip is armed.
 */
object TripBus {
    val fix = MutableStateFlow<Fix?>(null)
    val tracking = MutableStateFlow(false)
    val alarming = MutableStateFlow(false)
    val etaSec = MutableStateFlow<Int?>(null)
    val routeKm = MutableStateFlow<Double?>(null)
    val routeLine = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
}
