package com.rork.gpssimulator.ui

import android.app.Application
import android.util.Log
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
import com.rork.gpssimulator.location.MockResult
import com.rork.gpssimulator.location.PlaceResult
import com.rork.gpssimulator.location.RealLocationSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

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

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = AppRepository(app)
    private val mockController = MockLocationController(app)
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

    /**
     * Place details of the coordinate the running session is mocking.
     *
     * This is deliberately separate from [selected]: while a session runs,
     * [selected] holds only a *candidate* the user is considering, so the UI can
     * offer "Move Here" without ever implying the live session changed.
     */
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

    private val _geofenceEvents = MutableStateFlow<List<GeofenceEvent>>(emptyList())
    val geofenceEvents: StateFlow<List<GeofenceEvent>> = _geofenceEvents.asStateFlow()

    private val _geofenceInside = MutableStateFlow<Set<String>>(emptySet())
    val geofenceInside: StateFlow<Set<String>> = _geofenceInside.asStateFlow()

    /** Set when the map should recentre (e.g. after "my location" or stop). */
    private val _cameraTarget = MutableStateFlow<Pair<LatLng, Float?>?>(null)
    val cameraTarget: StateFlow<Pair<LatLng, Float?>?> = _cameraTarget.asStateFlow()

    private var pushJob: Job? = null
    private var timerJob: Job? = null
    private var searchJob: Job? = null
    private var resolveJob: Job? = null
    private var sessionStartedAt = 0L
    private var routeDistanceTraveled = 0.0
    private var joystickVector: Pair<Float, Float>? = null

    init {
        refreshPermissionState()
        val restored = settings.value
        if (restored.restoreLastLocation &&
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

    // ---------- Permissions & readiness ----------

    fun refreshPermissionState() {
        val granted = realLocation.hasPermission()
        _permissionGranted.value = granted
        _mockReadiness.value = mockController.checkReadiness(granted)
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
            if (_mockState.value != MockState.MOCK_ACTIVE) {
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

    // ---------- Mock session lifecycle ----------

    /**
     * Starts the system-level mock location. The MOCK_ACTIVE state is only entered
     * when the platform confirms the test provider actually started.
     */
    fun startMock(kind: SessionKind = SessionKind.STATIC, route: SimRoute? = null) {
        val point = _selected.value?.point ?: run {
            postMessage("Select a location first", isError = true)
            return
        }
        if (!_permissionGranted.value) {
            postMessage("Location permission is required", isError = true)
            _mockReadiness.value = MockReadiness.PERMISSION_REQUIRED
            return
        }

        val config = settings.value
        val result = mockController.start(
            point = applyRandomization(point, config),
            accuracy = config.accuracyM,
            altitude = config.altitudeM.toDouble(),
            speed = 0f,
            bearing = config.bearingDeg,
        )

        when (result) {
            is MockResult.Failure -> {
                _mockReadiness.value = when (result.reason) {
                    MockFailure.NOT_SELECTED_AS_MOCK_APP -> MockReadiness.DEV_OPTIONS_REQUIRED
                    MockFailure.MISSING_PERMISSION -> MockReadiness.PERMISSION_REQUIRED
                    MockFailure.PROVIDER_ERROR -> MockReadiness.SETUP_REQUIRED
                }
                postMessage(result.message, isError = true)
                return
            }
            MockResult.Success -> Unit
        }

        _mockState.value = MockState.MOCK_ACTIVE
        _mockReadiness.value = MockReadiness.READY
        _activePoint.value = point
        _activePlace.value = _selected.value?.copy(point = point)
        // The candidate has been consumed; a non-null selection from now on means
        // "a new candidate is pending", which is what drives the Move Here action.
        _selected.value = null
        _sessionKind.value = kind
        _isPaused.value = false
        _currentSpeedKmh.value = 0.0
        sessionStartedAt = System.currentTimeMillis()
        routeDistanceTraveled = 0.0

        repository.updateSettings { it.copy(lastMockLat = point.lat, lastMockLng = point.lng) }

        if (kind == SessionKind.ROUTE && route != null) {
            _routeProgress.value = RouteProgress(route, 0.0, route.distanceMeters, route.speedKmh, false)
            startRouteLoop(route)
        } else {
            startStaticLoop()
        }
        startTimer()
        evaluateGeofences(point)
    }

    /**
     * Redirects the *already running* session to [point].
     *
     * The system test providers stay installed and the session keeps its identity
     * (timer, history entry): only the pushed coordinate changes. This is what
     * makes A → B switching instant for a developer testing a flow.
     */
    fun moveMockTo(point: LatLng) {
        if (_mockState.value != MockState.MOCK_ACTIVE) {
            startMock()
            return
        }
        if (!point.isValid()) {
            postMessage("Invalid coordinate", isError = true)
            return
        }

        val config = settings.value
        val result = mockController.push(
            point = applyRandomization(point, config),
            accuracy = config.accuracyM,
            altitude = config.altitudeM.toDouble(),
            speed = 0f,
            bearing = config.bearingDeg,
        )
        if (result is MockResult.Failure) {
            postMessage(result.message, isError = true)
            endSession(returnToReal = settings.value.returnToRealOnStop)
            return
        }

        // Jumping to a fixed coordinate ends any route playback, but keeps the
        // same mock session alive.
        if (_sessionKind.value == SessionKind.ROUTE) {
            pushJob?.cancel()
            _routeProgress.value = null
            _sessionKind.value = SessionKind.STATIC
            startStaticLoop()
        }

        _activePoint.value = point
        _activePlace.value = _selected.value?.copy(point = point)
            ?: SelectedLocation(point, "", "")
        _selected.value = null
        _isPaused.value = false
        _currentSpeedKmh.value = 0.0
        joystickVector = null
        repository.updateSettings { it.copy(lastMockLat = point.lat, lastMockLng = point.lng) }
        moveCamera(point)
        evaluateGeofences(point)
    }

    /**
     * Stops mocking completely and returns the app to the REAL GPS state.
     *
     * The real fix is always re-read so the app is truly back on device GPS; the
     * "return to real location" preference only decides whether the camera follows.
     */
    fun stopMock() {
        endSession(returnToReal = true)
    }

    private fun endSession(returnToReal: Boolean) {
        val wasActive = _mockState.value == MockState.MOCK_ACTIVE
        pushJob?.cancel()
        pushJob = null
        timerJob?.cancel()
        timerJob = null
        mockController.stop()

        if (wasActive) {
            recordHistory()
        }

        _mockState.value = MockState.REAL_GPS
        _activePoint.value = null
        _activePlace.value = null
        _selected.value = null
        _routeProgress.value = null
        _sessionKind.value = SessionKind.STATIC
        _isPaused.value = false
        _elapsedMs.value = 0L
        _currentSpeedKmh.value = 0.0
        joystickVector = null
        repository.updateSettings { it.copy(lastMockLat = Double.NaN, lastMockLng = Double.NaN) }

        if (wasActive && returnToReal) {
            returnToRealLocation(recenter = settings.value.returnToRealOnStop)
        }
        refreshPermissionState()
    }

    /**
     * Freezes or resumes dynamic movement (route playback / joystick) while the
     * mock session itself stays fully alive: providers stay installed and the
     * last mocked coordinate keeps being pushed. This is deliberately NOT a stop —
     * only [stopMock] returns the device to real GPS.
     */
    fun togglePause() {
        if (_mockState.value != MockState.MOCK_ACTIVE) return
        val paused = !_isPaused.value
        _isPaused.value = paused
        _routeProgress.value = _routeProgress.value?.copy(isPaused = paused)
        if (paused) {
            _currentSpeedKmh.value = 0.0
            // Hold position immediately rather than waiting for the next tick.
            _activePoint.value?.let { push(it, 0.0, settings.value) }
        }
    }

    private fun recordHistory() {
        val point = _activePoint.value ?: return
        val selection = _activePlace.value
        repository.addHistory(
            HistoryEntry(
                id = UUID.randomUUID().toString(),
                name = selection?.name?.takeIf { it.isNotBlank() } ?: "Dropped pin",
                address = selection?.address.orEmpty(),
                point = point,
                startedAt = sessionStartedAt,
                durationMs = System.currentTimeMillis() - sessionStartedAt,
                kind = _sessionKind.value,
            ),
        )
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

    // ---------- Location push loops ----------

    private fun startStaticLoop() {
        pushJob?.cancel()
        pushJob = viewModelScope.launch {
            while (true) {
                val config = settings.value
                delay(config.updateIntervalMs.toLong().coerceIn(200L, 10_000L))
                if (_mockState.value != MockState.MOCK_ACTIVE) break

                // Paused freezes movement but must NOT stop mocking: keep pushing
                // the current coordinate so Android holds the mocked fix instead
                // of falling back to the real GPS provider.
                if (_isPaused.value) {
                    val held = _activePoint.value ?: break
                    if (!push(held, 0.0, config)) break
                    continue
                }

                val base = _activePoint.value ?: break
                val target = joystickVector?.let { (dirX, dirY) ->
                    advanceJoystick(base, dirX, dirY, config)
                } ?: applyRandomization(base, config)

                if (joystickVector != null) _activePoint.value = target

                if (!push(target, _currentSpeedKmh.value, config)) break
            }
        }
    }

    private fun startRouteLoop(route: SimRoute) {
        pushJob?.cancel()
        pushJob = viewModelScope.launch {
            val points = if (route.loop && route.points.size > 1) {
                route.points + route.points.first()
            } else {
                route.points
            }
            val total = points.zipWithNext().sumOf { (a, b) -> a.distanceTo(b) }
            if (total <= 0.0) return@launch

            var traveled = 0.0
            while (true) {
                val config = settings.value
                val stepMs = config.updateIntervalMs.toLong().coerceIn(200L, 5_000L)
                delay(stepMs)
                if (_mockState.value != MockState.MOCK_ACTIVE) break

                // Freeze at the current coordinate without surrendering the mock:
                // `traveled` is not advanced, so Resume continues from here.
                if (_isPaused.value) {
                    val held = _activePoint.value ?: break
                    if (!push(held, 0.0, config)) break
                    continue
                }

                val speedKmh = route.speedKmh
                val stepMeters = (speedKmh / 3.6) * (stepMs / 1000.0)
                traveled += stepMeters

                if (traveled >= total) {
                    if (route.loop) {
                        traveled = 0.0
                    } else {
                        val end = points.last()
                        _activePoint.value = end
                        _routeProgress.value = _routeProgress.value?.copy(
                            traveledMeters = total,
                            totalMeters = total,
                        )
                        _currentSpeedKmh.value = 0.0
                        push(end, 0.0, config)
                        evaluateGeofences(end)
                        break
                    }
                }

                val position = pointAlong(points, traveled, config.routeInterpolation)
                _activePoint.value = position.first
                _currentBearing.value = position.second
                _currentSpeedKmh.value = speedKmh
                _routeProgress.value = _routeProgress.value?.copy(
                    traveledMeters = min(traveled, total),
                    totalMeters = total,
                    speedKmh = speedKmh,
                )

                if (!push(position.first, speedKmh, config, position.second)) break
                evaluateGeofences(position.first)
            }
        }
    }

    /** Pushes one update; returns false when the platform revoked mocking. */
    private fun push(
        point: LatLng,
        speedKmh: Double,
        config: AppSettings,
        bearingOverride: Float? = null,
    ): Boolean {
        val result = mockController.push(
            point = point,
            accuracy = config.accuracyM,
            altitude = config.altitudeM.toDouble(),
            speed = (speedKmh / 3.6).toFloat(),
            bearing = bearingOverride ?: config.bearingDeg,
        )
        if (result is MockResult.Failure) {
            Log.w(TAG, "Mock push failed, stopping session")
            postMessage(result.message, isError = true)
            endSession(returnToReal = settings.value.returnToRealOnStop)
            return false
        }
        return true
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_mockState.value == MockState.MOCK_ACTIVE) {
                _elapsedMs.value = System.currentTimeMillis() - sessionStartedAt
                delay(1000L)
            }
        }
    }

    /** Position and bearing at [distance] meters along the polyline. */
    private fun pointAlong(
        points: List<LatLng>,
        distance: Double,
        interpolate: Boolean,
    ): Pair<LatLng, Float> {
        var remaining = distance
        for (i in 0 until points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            val segment = a.distanceTo(b)
            if (segment <= 0.0) continue
            if (remaining <= segment) {
                val t = (remaining / segment).coerceIn(0.0, 1.0)
                val position = if (interpolate) a.lerp(b, t) else a
                return position to a.bearingTo(b)
            }
            remaining -= segment
        }
        val last = points.last()
        val prev = points.getOrElse(points.lastIndex - 1) { last }
        return last to prev.bearingTo(last)
    }

    private fun applyRandomization(point: LatLng, config: AppSettings): LatLng {
        if (!config.randomizeEnabled && !config.coordinateVariation) return point
        val radius = if (config.randomizeEnabled) config.randomizationRadiusM.toDouble() else 1.5
        if (radius <= 0.0) return point
        // Uniform sample inside the circle, so drift never exceeds the radius.
        val angle = Random.nextDouble(0.0, 360.0)
        val magnitude = radius * kotlin.math.sqrt(Random.nextDouble(0.0, 1.0))
        return point.offset(magnitude, angle)
    }

    private fun advanceJoystick(
        base: LatLng,
        dirX: Float,
        dirY: Float,
        config: AppSettings,
    ): LatLng {
        val magnitude = kotlin.math.hypot(dirX, dirY).coerceIn(0f, 1f)
        if (magnitude < 0.05f) {
            _currentSpeedKmh.value = 0.0
            return base
        }
        val speedKmh = config.speedKmh * magnitude
        _currentSpeedKmh.value = speedKmh
        val stepMeters = (speedKmh / 3.6) * (config.updateIntervalMs / 1000.0)
        // Screen up (negative Y) is north.
        val bearing = (Math.toDegrees(kotlin.math.atan2(dirX.toDouble(), -dirY.toDouble())) + 360.0) % 360.0
        _currentBearing.value = bearing.toFloat()
        val next = base.offset(stepMeters, bearing)
        evaluateGeofences(next)
        return next
    }

    /** Feeds a normalised joystick vector; (0,0) stops movement. */
    fun setJoystickVector(x: Float, y: Float) {
        joystickVector = if (kotlin.math.hypot(x, y) < 0.05f) null else x to y
        if (joystickVector == null) _currentSpeedKmh.value = 0.0
        if (_mockState.value == MockState.MOCK_ACTIVE && _sessionKind.value != SessionKind.ROUTE) {
            _sessionKind.value = if (joystickVector != null) SessionKind.JOYSTICK else SessionKind.STATIC
        }
    }

    // ---------- Routes ----------

    fun startRoute(route: SimRoute) {
        if (route.points.size < 2) {
            postMessage("Add at least two points", isError = true)
            return
        }
        val start = route.points.first()
        // A route is a different kind of session, so the previous one ends — but
        // without bouncing the camera back to the real position first.
        if (_mockState.value == MockState.MOCK_ACTIVE) endSession(returnToReal = false)
        _selected.value = SelectedLocation(start, route.name, "")
        moveCamera(start)
        startMock(SessionKind.ROUTE, route)
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

    /** Recomputes inside/outside membership and logs transitions. */
    private fun evaluateGeofences(point: LatLng) {
        val fences = geofences.value
        if (fences.isEmpty()) return
        val previous = _geofenceInside.value
        val current = fences.filter { point.distanceTo(it.center) <= it.radiusMeters }
            .map { it.id }
            .toSet()

        val newEvents = mutableListOf<GeofenceEvent>()
        fences.forEach { fence ->
            val wasInside = previous.contains(fence.id)
            val isInside = current.contains(fence.id)
            if (wasInside != isInside) {
                newEvents.add(
                    GeofenceEvent(
                        geofenceId = fence.id,
                        geofenceName = fence.name,
                        entered = isInside,
                        timestamp = System.currentTimeMillis(),
                    ),
                )
            }
        }
        if (newEvents.isNotEmpty()) {
            _geofenceEvents.value = (newEvents + _geofenceEvents.value).take(60)
        }
        _geofenceInside.value = current
    }

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
                mockReadiness = mockController.checkReadiness(granted),
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
        if (_mockState.value == MockState.MOCK_ACTIVE) endSession(returnToReal = false)
        repository.deleteAllLocalData()
        _geofenceEvents.value = emptyList()
        _geofenceInside.value = emptySet()
        _diagnostics.value = null
    }

    // ---------- Messages ----------

    fun postMessage(text: String, isError: Boolean = false) {
        _message.value = UiMessage(text, isError)
    }

    fun consumeMessage() {
        _message.value = null
    }

    // ---------- Lifecycle ----------

    /** Called when the app is intentionally closed. */
    fun onAppClosing() {
        if (settings.value.stopOnAppClose && _mockState.value == MockState.MOCK_ACTIVE) {
            endSession(returnToReal = false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        pushJob?.cancel()
        timerJob?.cancel()
        realLocation.stopUpdates()
        if (settings.value.stopOnAppClose) {
            mockController.stop()
        }
    }

    companion object {
        private const val TAG = "AppViewModel"
    }
}
