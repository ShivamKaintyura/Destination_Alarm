package com.destinationalarm.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.destinationalarm.app.alarm.AlarmService
import com.destinationalarm.app.data.Prefs
import com.destinationalarm.app.model.Fix
import com.destinationalarm.app.model.SavedPlace
import com.destinationalarm.app.model.Trip
import com.destinationalarm.app.model.TripBus
import com.destinationalarm.app.net.Net
import com.destinationalarm.app.service.TrackingControl
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent

data class UiState(
    val trip: Trip? = null,
    val tracking: Boolean = false,
    val fix: Fix? = null,
    val etaSec: Int? = null,
    val routeKm: Double? = null,
    val routeLine: List<Pair<Double, Double>> = emptyList(),
    val query: String = "",
    val searching: Boolean = false,
    val results: List<Net.Place> = emptyList(),
    val saved: List<SavedPlace> = emptyList(),
    val message: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(
        UiState(trip = Prefs.trip(app), saved = Prefs.savedPlaces(app))
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            TripBus.tracking.collect { on -> _state.update { it.copy(tracking = on) } }
        }
        viewModelScope.launch {
            TripBus.fix.collect { f -> _state.update { it.copy(fix = f) } }
        }
        viewModelScope.launch {
            TripBus.etaSec.collect { e -> _state.update { it.copy(etaSec = e) } }
        }
        viewModelScope.launch {
            TripBus.routeKm.collect { k -> _state.update { it.copy(routeKm = k) } }
        }
        viewModelScope.launch {
            TripBus.routeLine.collect { line -> _state.update { it.copy(routeLine = line) } }
        }
    }

    /* ---------------- saved places ---------------- */

    fun useSaved(place: SavedPlace) {
        setTrip(place.lat, place.lon, place.name)
        _state.update { it.copy(query = "", results = emptyList()) }
    }

    /** Saves the current destination under a short label (Home / Work / custom). */
    fun saveCurrentAs(label: String): Boolean {
        val trip = _state.value.trip ?: return false
        val clean = label.trim().ifBlank { "Place" }.take(18)
        val current = _state.value.saved

        val updated = current.filterNot { it.label.equals(clean, ignoreCase = true) } +
            SavedPlace(
                id = System.currentTimeMillis().toString(),
                label = clean,
                name = trip.name,
                lat = trip.lat,
                lon = trip.lon
            )

        val capped = updated.takeLast(8)
        Prefs.saveSavedPlaces(getApplication(), capped)
        _state.update { it.copy(saved = capped) }
        return true
    }

    fun removeSaved(id: String) {
        val updated = _state.value.saved.filterNot { it.id == id }
        Prefs.saveSavedPlaces(getApplication(), updated)
        _state.update { it.copy(saved = updated) }
    }

    /** Wipes every local preference, trip and saved place. */
    fun deleteAllData() {
        TrackingControl.stop(getApplication())
        Prefs.clearAllData(getApplication())
        TripBus.tracking.value = false
        TripBus.alarming.value = false
        TripBus.etaSec.value = null
        TripBus.routeKm.value = null
        TripBus.routeLine.value = emptyList()
        _state.update {
            it.copy(
                trip = null,
                tracking = false,
                etaSec = null,
                routeKm = null,
                routeLine = emptyList(),
                saved = emptyList(),
                query = "",
                results = emptyList()
            )
        }
    }

    /* ---------------- search ---------------- */

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        searchJob?.cancel()

        if (q.trim().length < 3) {
            _state.update { it.copy(results = emptyList(), searching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(320)
            _state.update { it.copy(searching = true) }
            val results = withContext(Dispatchers.IO) {
                runCatching { Net.search(q, _state.value.fix) }.getOrDefault(emptyList())
            }
            _state.update { it.copy(results = results, searching = false) }
        }
    }

    fun pickPlace(place: Net.Place) {
        setTrip(place.lat, place.lon, place.name)
        _state.update { it.copy(query = "", results = emptyList()) }
    }

    /** Called from the Uber-style pin picker once the user confirms the spot. */
    fun confirmDestination(lat: Double, lon: Double, name: String) {
        val clean = name.trim().ifBlank { "Selected location" }
        setTrip(lat, lon, clean)
        _state.update { it.copy(query = "", results = emptyList()) }
    }

    fun setPin(lat: Double, lon: Double) {
        setTrip(lat, lon, "Dropped pin")
        _state.update { it.copy(query = "", results = emptyList()) }
        reverseName(lat, lon)
    }

    private fun setTrip(lat: Double, lon: Double, name: String) {
        val current = _state.value.trip
        val trip = Trip(
            lat = lat,
            lon = lon,
            name = name,
            radiusM = current?.radiusM ?: 500,
            minsBefore = current?.minsBefore ?: 0,
            mode = current?.mode ?: "DRIVE"
        )
        _state.update { it.copy(trip = trip) }
        if (_state.value.tracking) {
            persistAndReload(trip)
        }
    }

    private fun reverseName(lat: Double, lon: Double) {
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) {
                runCatching { Net.reverseName(lat, lon) }.getOrNull()
            }
            val trip = _state.value.trip ?: return@launch
            if (name.isNullOrBlank()) return@launch
            if (trip.lat != lat || trip.lon != lon) return@launch

            val updated = trip.copy(name = name)
            _state.update { it.copy(trip = updated) }
            if (_state.value.tracking) persistAndReload(updated)
        }
    }

    /* ---------------- trip settings ---------------- */

    fun setRadius(radiusM: Int) = updateTrip { it.copy(radiusM = radiusM) }

    fun setMinsBefore(mins: Int) = updateTrip { it.copy(minsBefore = mins) }

    fun setMode(mode: String) = updateTrip { it.copy(mode = mode) }

    private fun updateTrip(transform: (Trip) -> Trip) {
        val trip = _state.value.trip?.let(transform) ?: return
        _state.update { it.copy(trip = trip) }
        if (_state.value.tracking) persistAndReload(trip)
    }

    private fun persistAndReload(trip: Trip) {
        Prefs.saveTrip(getApplication(), trip)
        TrackingControl.reload(getApplication())
    }

    /* ---------------- tracking ---------------- */

    fun start() {
        val trip = _state.value.trip
        if (trip == null) {
            _state.update { it.copy(message = "Set a destination first") }
            return
        }
        Prefs.saveTrip(getApplication(), trip)
        Prefs.clearSnooze(getApplication())
        TrackingControl.start(getApplication())
    }

    fun stop() {
        Prefs.clearTrip(getApplication())
        Prefs.clearSnooze(getApplication())
        TrackingControl.stop(getApplication())
        TripBus.tracking.value = false
        _state.update { it.copy(tracking = false, etaSec = null, routeLine = emptyList()) }
    }

    fun clearDestination() {
        stop()
        _state.update { it.copy(trip = null) }
    }

    fun testAlarm() {
        val i = Intent(getApplication(), AlarmService::class.java)
            .setAction(AlarmService.ACTION_START)
            .putExtra(AlarmService.EXTRA_TITLE, "Test alarm")
            .putExtra(AlarmService.EXTRA_SUB, "This is how it will sound")
        runCatching { ContextCompat.startForegroundService(getApplication(), i) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }
}
