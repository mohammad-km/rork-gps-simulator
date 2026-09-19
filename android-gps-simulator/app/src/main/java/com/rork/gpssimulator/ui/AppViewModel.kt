package com.rork.gpssimulator.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rork.gpssimulator.data.AppRepository
import com.rork.gpssimulator.data.AppSettings
import com.rork.gpssimulator.data.model.Geofence
import com.rork.gpssimulator.data.model.GeofenceEvent
import com.rork.gpssimulator.data.model.HistoryEntry
import com.rork.gpssimulator.data.model.LatLng
import com.rork.gpssimulator.data.model.LocationSample
import com.rork.gpssimulator.data.model.MockReadiness
import com.rork.gpssimulator.data.model.MockState
import com.rork.gpssimulator.data.model.SavedKind
import com.rork.gpssimulator.data.model.SavedLocation
import com.rork.gpssimulator.data.model.SessionKind
import com.rork.gpssimulator.data.model.SimRoute
import com.rork.gpssimulator.location.GeocodingService
import com.rork.gpssimulator.location.MockFailure
import com.rork.gpssimulator.location.MockLocationController
import com.rork.gpssimulator.location.MockLocationService
import com.rork.gpssimulator.location.MockResult
import com.rork.gpssimulator.location.MockSessionBus
import com.rork.gpssimulator.location.PlaceResult
import com.rork.gpssimulator.location.RealLocationSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** The coordinate the user has aimed at and confirmed, awaiting start. */
data class SelectedLocation(
    val point: LatLng,
    val name: String,
    val address: String,
    val isResolving: Boolean = false,
)

/** Live progress of a running route simulation. */
data class RouteProgress(
    val route: SimRoute,
    val traveledMeters: Double,
    val totalMeters: Double,
    val speedKmh: Double,
    val isPaused: Boolean,
) {
    val remainingMeters: Double get() = (totalMeters - traveledMeters).coerceAtLeast(0.0)
    val etaSeconds: Double
        get() = if (speedKmh <= 0.0) 0.0 else remainingMeters / (speedKmh / 3.6)
}

/** One-shot user-facing message. */
data class UiMessage(val text: String, val isError: Boolean = false, val id: Long = System.nanoTime())

/** Result rows for the diagnostics screen. */
data class DiagnosticsReport(
    val realGpsAvailable: Boolean,
    val permissionGranted: Boolean,
    val mockReadiness: MockReadiness,
    val providerName: String,
    val networkAvailable: Boolean,
    val gpsEnabled: Boolean,
    val sample: LocationSample?,
    val ranAt: Long,
)

/**
 * UI-facing state holder. The active mock session itself is no longer owned
 * here — it lives in [MockLocationService], a foreground service independent
 * of this ViewModel's lifecycle. This class only:
 *  - manages the pending "candidate" location the user is aiming at (a UI
 *    concept that never touches the system location, so it belongs here),
 *  - sends start/move/pause/stop/joystick commands to the bound service, and
 *  - mirrors [MockSessionBus]'s live state into the StateFlows the screens
 *    already read, so no screen needed to change.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = AppRepository.getInstance(app)

    /** Used only for the read-only "is mocking possible right now" probe — never to start/push a session. */
    private val readinessProbe = MockLocationController(app)
    private val realLocation = RealLocationSource(app)
    private val geocoder = GeocodingService()

    val settings: StateFlow<AppSettings> = repository.settings
    val savedLocations: StateFlow<List<SavedLocation>> = repository.saved
    val routes: StateFlow<List<SimRoute>> = repository.routes
    val history: StateFlow<List<HistoryEntry>> = repository.history
    val geofences: StateFlow<List<Geofence>> = repository.geofences
    val recentSearches: StateFlow<List<String>> = repository.recentSearches

    private val _mockState = MutableStateFlow(MockState.REAL_GPS)
    val mockState: StateFlow<MockState> = _mockState.asStateFlow()

    private val _selected = MutableStateFlow<SelectedLocation?>(null)
    val selected: StateFlow<SelectedLocation?> = _selected.asStateFlow()

    /** The coordinate currently being pushed to the system. */
    private val _activePoint = MutableStateFlow<LatLng?>(null)
    val activePoint: StateFlow<LatLng?> = _activePoint.asStateFlow()

    private val _activePlace = MutableStateFlow<SelectedLocation?>(null)
    val activePlace: StateFlow<SelectedLocation?> = _activePlace.asStateFlow()

    private val _realSample = MutableStateFlow<LocationSample?>(null)
    val realSample: StateFlow<LocationSample?> = _realSample.asStateFlow()

    private val _mockReadiness = MutableStateFlow(MockReadiness.SETUP_REQUIRED)
    val mockReadiness: StateFlow<MockReadiness> = _mockReadiness.asStateFlow()

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private val _currentSpeedKmh = MutableStateFlow(0.0)
    val currentSpeedKmh: StateFlow<Double> = _currentSpeedKmh.asStateFlow()

    private val _currentBearing = MutableStateFlow(0f)
    val currentBearing: StateFlow<Float> = _currentBearing.asStateFlow()

    private val _routeProgress = MutableStateFlow<RouteProgress?>(null)
    val routeProgress: StateFlow<RouteProgress?> = _routeProgress.asStateFlow()

    private val _sessionKind = MutableStateFlow(SessionKind.STATIC)
    val sessionKind: StateFlow<SessionKind> = _sessionKind.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _searchResults = MutableStateFlow<List<PlaceResult>>(emptyList())
    val searchResults: StateFlow<List<PlaceResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    private val _diagnostics = MutableStateFlow<DiagnosticsReport?>(null)
    val diagnostics: StateFlow<DiagnosticsReport?> = _diagnostics.asStateFlow()

    private val _isDiagnosticsRunning = MutableStateFlow(false)
    val isDiagnosticsRunning: StateFlow<Boolean> = _isDiagnosticsRunning.asStateFlow()

    /** Geofence membership is evaluated inside the service's push loop; this just mirrors it. */
    val geofenceEvents: StateFlow<List<GeofenceEvent>> = MockSessionBus.geofenceEvents
    val geofenceInside: StateFlow<Set<String>> = MockSessionBus.geofenceInside

    /** Set when the map should recentre (e.g. after "my location" or stop). */
    private val _cameraTarget = MutableStateFlow<Pair<LatLng, Float?>?>(null)
    val cameraTarget: StateFlow<Pair<LatLng, Float?>?> = _cameraTarget.asStateFlow()

    private var searchJob: Job? = null
    private var resolveJob: Job? = null
    private var elapsedTickerJob: Job? = null
    private var wasSessionActive = false

    // ---------- Service binding ----------

    @Volatile
    private var boundService: MockLocationService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            boundService = (binder as? MockLocationService.LocalBinder)?.getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundService = null
        }
    }

    init {
        // Bind eagerly (without starting it as foreground) so that by the time
        // the user presses Start, the direct method-call path to the service is
        // already available. Binding alone does not promote it to foreground —
        // that only happens inside MockLocationService.startSession.
        app.bindService(Intent(app, MockLocationService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)

        viewModelScope.launch {
            MockSessionBus.session.collect { session -> applySession(session) }
        }
        viewModelScope.launch {
            MockSessionBus.events.collect { event ->
                when (event) {
                    is MockSessionBus.Event.Failure -> postMessage(event.message, isError = true)
                    MockSessionBus.Event.RouteFinished -> Unit
                }
            }
        }

        refreshPermissionState()
        val restored = settings.value
        if (restored.restoreLastLocation &&
            MockSessionBus.session.value == null &&
            !restored.lastMockLat.isNaN() && !restored.lastMockLng.isNaN()
        ) {
            val point = LatLng(restored.lastMockLat, restored.lastMockLng)
            if (point.isValid()) {
                _selected.value = SelectedLocation(point, "", "", isResolving = true)
                _mockState.value = MockState.SELECTED
                resolvePlace(point)
            }
        }
    }

    /** Reflects the service's live session into every screen-facing StateFlow. */
    private fun applySession(session: MockSessionBus.Session?) {
        if (session != null) {
            _mockState.value = MockState.MOCK_ACTIVE
            _activePoint.value = session.point
            _activePlace.value = SelectedLocation(session.point, session.placeName, session.placeAddress)
            _sessionKind.value = session.kind
            _isPaused.value = session.isPaused
            _currentSpeedKmh.value = session.speedKmh
            _currentBearing.value = session.bearing
            _routeProgress.value = session.route?.let { route ->
                RouteProgress(route, session.routeTraveledMeters, route.distanceMeters, session.speedKmh, session.isPaused)
            }
            ensureElapsedTicker(session.sessionStartedAt)
        } else {
            val wasActive = wasSessionActive
            _activePoint.value = null
            _activePlace.value = null
            _sessionKind.value = SessionKind.STATIC
            _isPaused.value = false
            _currentSpeedKmh.value = 0.0
            _currentBearing.value = 0f
            _routeProgress.value = null
            elapsedTickerJob?.cancel()
            _elapsedMs.value = 0L
            _mockState.value = if (_selected.value != null) MockState.SELECTED else MockState.REAL_GPS

            if (wasActive) {
                _selected.value = null
                returnToRealLocation(recenter = settings.value.returnToRealOnStop)
                refreshPermissionState()
            }
        }
        wasSessionActive = session != null
    }

    private fun ensureElapsedTicker(sessionStartedAt: Long) {
        if (elapsedTickerJob?.isActive == true) return
        elapsedTickerJob = viewModelScope.launch {
            while (MockSessionBus.session.value != null) {
                _elapsedMs.value = System.currentTimeMillis() - sessionStartedAt
                delay(1000L)
            }
        }
    }

    // ---------- Permissions & readiness ----------

    fun refreshPermissionState() {
        val granted = realLocation.hasPermission()
        _permissionGranted.value = granted
        // A readiness probe briefly installs/removes its own test provider,
        // which would clobber the real provider MockLocationService is
        // actively feeding. When a session is running, readiness is READY by
        // definition — skip the probe entirely.
        _mockReadiness.value = if (MockSessionBus.session.value != null) {
            MockReadiness.READY
        } else {
            readinessProbe.checkReadiness(granted)
        }
        if (granted) {
            realLocation.lastKnown(settings.value.usePlayServices)?.let { _realSample.value = it }
            startRealUpdates()
        }
    }

    private fun startRealUpdates() {
        if (!_permissionGranted.value) return
        realLocation.startUpdates(settings.value.usePlayServices, 2000L) { sample ->
            // While mocking, the system feed echoes our own test location; keep the
            // last genuine fix so "return to real GPS" still has somewhere to go.
            if (MockSessionBus.session.value == null) {
                _realSample.value = sample
            }
        }
    }

    // ---------- Selection ----------

    /** Confirms the crosshair coordinate and moves to the SELECTED state. */
    fun selectPoint(point: LatLng) {
        if (!point.isValid()) {
            postMessage("Invalid coordinate", isError = true)
            return
        }
        _selected.value = SelectedLocation(point, "", "", isResolving = true)
        if (_mockState.value != MockState.MOCK_ACTIVE) {
            _mockState.value = MockState.SELECTED
        }
        resolvePlace(point)
    }

    fun selectSaved(location: SavedLocation) {
        _selected.value = SelectedLocation(location.point, location.name, location.address)
        if (_mockState.value != MockState.MOCK_ACTIVE) {
            _mockState.value = MockState.SELECTED
        }
        moveCamera(location.point)
    }

    /**
     * Cancels only the pending candidate. A running mock session is never
     * affected — stopping is exclusively the red X ([stopMock]).
     */
    fun clearSelection() {
        resolveJob?.cancel()
        _selected.value = null
        if (_mockState.value == MockState.SELECTED) {
            _mockState.value = MockState.REAL_GPS
        }
    }

    private fun resolvePlace(point: LatLng) {
        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            val place = geocoder.reverse(point)
            val current = _selected.value ?: return@launch
            if (current.point != point) return@launch
            _selected.value = current.copy(
                name = place?.name ?: "",
                address = place?.address ?: "",
                isResolving = false,
            )
        }
    }

    // ---------- Mock session control (delegates to MockLocationService) ----------

    /**
     * Starts the system-level mock location. The MOCK_ACTIVE state is only
     * reflected once [MockSessionBus] confirms the service actually started —
     * this call itself only reports whether the *request* was accepted.
     */
    fun startMock(kind: SessionKind = SessionKind.STATIC, route: SimRoute? = null) {
        val selection = _selected.value ?: run {
            postMessage("Select a location first", isError = true)
            return
        }
        if (!_permissionGranted.value) {
            postMessage("Location permission is required", isError = true)
            _mockReadiness.value = MockReadiness.PERMISSION_REQUIRED
            return
        }
        val service = requireBoundService() ?: return

        val context = getApplication<Application>()
        ContextCompat.startForegroundService(context, Intent(context, MockLocationService::class.java))
        val result = service.startSession(kind, selection.point, selection.name, selection.address, route)
        when (result) {
            is MockResult.Failure -> {
                _mockReadiness.value = when (result.reason) {
                    MockFailure.NOT_SELECTED_AS_MOCK_APP -> MockReadiness.DEV_OPTIONS_REQUIRED
                    MockFailure.MISSING_PERMISSION -> MockReadiness.PERMISSION_REQUIRED
                    MockFailure.PROVIDER_ERROR -> MockReadiness.SETUP_REQUIRED
                }
                postMessage(result.message, isError = true)
            }
            MockResult.Success -> {
                // The candidate has been consumed by the service; a non-null
                // selection from now on means "a new candidate is pending",
                // which is what drives the Move Here action.
                _selected.value = null
                _mockReadiness.value = MockReadiness.READY
            }
        }
    }

    /** Redirects the *already running* session to [point], or starts one if none is active. */
    fun moveMockTo(point: LatLng) {
        if (MockSessionBus.session.value == null) {
            startMock()
            return
        }
        if (!point.isValid()) {
            postMessage("Invalid coordinate", isError = true)
            return
        }
        val service = requireBoundService() ?: return
        val selection = _selected.value

        val result = service.moveTo(point, selection?.name.orEmpty(), selection?.address.orEmpty())
        when (result) {
            is MockResult.Failure -> postMessage(result.message, isError = true)
            MockResult.Success -> {
                _selected.value = null
                moveCamera(point)
            }
        }
    }

    /** Stops mocking completely and returns the app to the REAL GPS state. */
    fun stopMock() {
        boundService?.stopSession()
    }

    /**
     * Freezes or resumes dynamic movement (route playback / joystick) while the
     * mock session itself stays fully alive. This is deliberately NOT a stop —
     * only [stopMock] returns the device to real GPS.
     */
    fun togglePause() {
        boundService?.togglePause()
    }

    /** Feeds a normalised joystick vector; (0,0) stops movement. */
    fun setJoystickVector(x: Float, y: Float) {
        boundService?.setJoystickVector(x, y)
    }

    /**
     * Fetches the device's real position, optionally recentring the map on it.
     * The "My Location" button always recentres; stopping a session honours the
     * user's preference.
     */
    fun returnToRealLocation(recenter: Boolean = true) {
        val sample = realLocation.lastKnown(settings.value.usePlayServices) ?: _realSample.value
        if (sample != null) {
            _realSample.value = sample
            if (recenter) moveCamera(sample.point)
        } else if (!_permissionGranted.value) {
            postMessage("Location permission is required", isError = true)
        }
    }

    // ---------- Routes ----------

    fun startRoute(route: SimRoute) {
        if (route.points.size < 2) {
            postMessage("Add at least two points", isError = true)
            return
        }
        if (!_permissionGranted.value) {
            postMessage("Location permission is required", isError = true)
            _mockReadiness.value = MockReadiness.PERMISSION_REQUIRED
            return
        }
        val service = requireBoundService() ?: return
        val start = route.points.first()

        val context = getApplication<Application>()
        ContextCompat.startForegroundService(context, Intent(context, MockLocationService::class.java))
        val result = service.startSession(SessionKind.ROUTE, start, route.name, "", route)
        when (result) {
            is MockResult.Failure -> postMessage(result.message, isError = true)
            MockResult.Success -> {
                _selected.value = null
                moveCamera(start)
            }
        }
    }

    fun saveRoute(route: SimRoute) = repository.upsertRoute(route)

    fun deleteRoute(id: String) = repository.deleteRoute(id)

    // ---------- Saved locations ----------

    fun saveLocation(
        name: String,
        address: String,
        point: LatLng,
        kind: SavedKind,
        iconKey: String = "pin",
        notes: String = "",
        id: String? = null,
    ) {
        repository.upsertSaved(
            SavedLocation(
                id = id ?: UUID.randomUUID().toString(),
                name = name.ifBlank { "Saved location" },
                address = address,
                point = point,
                kind = kind,
                iconKey = iconKey,
                notes = notes,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    fun saveCurrentSelectionAsFavorite() {
        val selection = _selected.value ?: return
        saveLocation(
            name = selection.name.ifBlank { "Dropped pin" },
            address = selection.address,
            point = selection.point,
            kind = SavedKind.FAVORITE,
        )
    }

    fun deleteSaved(id: String) = repository.deleteSaved(id)

    fun clearSaved(kind: SavedKind) = repository.clearSaved(kind)

    fun replaceSaved(list: List<SavedLocation>) = repository.replaceSaved(list)

    fun replaceRoutes(list: List<SimRoute>) = repository.replaceRoutes(list)

    // ---------- History ----------

    fun deleteHistory(id: String) = repository.deleteHistory(id)

    fun clearHistory() = repository.clearHistory()

    // ---------- Geofences ----------

    fun addGeofence(name: String, center: LatLng, radius: Double) {
        repository.upsertGeofence(
            Geofence(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { "Geofence" },
                center = center,
                radiusMeters = radius,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    fun deleteGeofence(id: String) = repository.deleteGeofence(id)

    // ---------- Search ----------

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            delay(320) // debounce keystrokes
            val results = geocoder.search(query)
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    fun commitSearch(query: String) = repository.addRecentSearch(query)

    fun clearSearchResults() {
        searchJob?.cancel()
        _searchResults.value = emptyList()
        _isSearching.value = false
    }

    // ---------- Camera ----------

    fun moveCamera(point: LatLng, zoom: Float? = null) {
        _cameraTarget.value = point to zoom
    }

    fun consumeCameraTarget() {
        _cameraTarget.value = null
    }

    fun persistCamera(point: LatLng, zoom: Float) {
        if (!settings.value.rememberLastPosition) return
        repository.updateSettings {
            it.copy(lastCameraLat = point.lat, lastCameraLng = point.lng, lastCameraZoom = zoom)
        }
    }

    // ---------- Diagnostics ----------

    fun runDiagnostics() {
        viewModelScope.launch {
            _isDiagnosticsRunning.value = true
            refreshPermissionState()
            delay(650) // let a fresh fix arrive
            val granted = realLocation.hasPermission()
            val sample = realLocation.lastKnown(settings.value.usePlayServices) ?: _realSample.value
            _diagnostics.value = DiagnosticsReport(
                realGpsAvailable = realLocation.isLocationEnabled(),
                permissionGranted = granted,
                mockReadiness = if (MockSessionBus.session.value != null) {
                    MockReadiness.READY
                } else {
                    readinessProbe.checkReadiness(granted)
                },
                providerName = realLocation.activeProviderName(settings.value.usePlayServices),
                networkAvailable = realLocation.isNetworkAvailable(),
                gpsEnabled = realLocation.isGpsProviderEnabled(),
                sample = sample,
                ranAt = System.currentTimeMillis(),
            )
            _isDiagnosticsRunning.value = false
        }
    }

    fun isPlayServicesAvailable(): Boolean = realLocation.isPlayServicesProviderAvailable()

    fun providerName(): String = realLocation.activeProviderName(settings.value.usePlayServices)

    // ---------- Settings ----------

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val before = settings.value
        repository.updateSettings(transform)
        val after = settings.value
        if (before.usePlayServices != after.usePlayServices) {
            startRealUpdates()
        }
    }

    fun resetSettings() {
        repository.resetSettings()
        postMessage("Settings restored to defaults")
    }

    fun deleteAllLocalData() {
        if (MockSessionBus.session.value != null) boundService?.stopSession()
        repository.deleteAllLocalData()
        MockSessionBus.resetGeofenceState()
        _diagnostics.value = null
    }

    // ---------- Messages ----------

    fun postMessage(text: String, isError: Boolean = false) {
        _message.value = UiMessage(text, isError)
    }

    fun consumeMessage() {
        _message.value = null
    }

    // ---------- Helpers ----------

    private fun requireBoundService(): MockLocationService? {
        val service = boundService
        if (service == null) {
            Log.w(TAG, "Mock location service not yet bound; ignoring command")
            postMessage("Still starting up — try again in a moment", isError = true)
        }
        return service
    }

    // ---------- Lifecycle ----------

    override fun onCleared() {
        super.onCleared()
        elapsedTickerJob?.cancel()
        realLocation.stopUpdates()
        // Deliberately does NOT touch the mock session: MockLocationService
        // owns that lifetime entirely, independent of this ViewModel.
        runCatching { getApplication<Application>().unbindService(serviceConnection) }
    }

    companion object {
        private const val TAG = "AppViewModel"
    }
}
