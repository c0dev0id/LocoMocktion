package de.codevoid.locomocktion

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.codevoid.locomocktion.gpx.GpxTrack
import de.codevoid.locomocktion.gpx.TrackPoint
import de.codevoid.locomocktion.gpx.parseGpx
import de.codevoid.locomocktion.service.MockLocationService
import de.codevoid.locomocktion.service.TravelMode
import de.codevoid.locomocktion.ui.UnitSystem
import de.codevoid.locomocktion.updater.ReleaseInfo
import de.codevoid.locomocktion.updater.downloadApk
import de.codevoid.locomocktion.updater.fetchLatestRelease
import de.codevoid.locomocktion.updater.isNewerVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.UnknownHostException

data class UiState(
    val track: GpxTrack? = null,
    val fileName: String? = null,
    val speedKmh: Float = 20f,
    val customMode: Boolean = false,
    val useGpxSpeed: Boolean = false,
    val updateIntervalMs: Long = 1000L,
    val startKm: Float = 0f,
    val endKm: Float = 0f,
    val travelMode: TravelMode = TravelMode.Normal,
    val unitSystem: UnitSystem = UnitSystem.Metric,
    val error: String? = null,
    val isLoading: Boolean = false,
    val availableTracks: List<GpxTrack>? = null,
    val showTrackSelector: Boolean = false,
    val pendingUri: String? = null,
    val pendingDisplayName: String? = null,
    val useFixedLocation: Boolean = false,
    val fixedLat: String = "",
    val fixedLon: String = "",
) {
    val fixedPoint: TrackPoint?
        get() = parseLatitude(fixedLat)?.let { lat -> parseLongitude(fixedLon)?.let { lon -> TrackPoint(lat, lon) } }
    val canStart: Boolean get() = if (useFixedLocation) fixedPoint != null else track != null
    val hasMultipleTracks: Boolean get() = (availableTracks?.size ?: 0) > 1
}

fun parseLatitude(text: String): Double? = text.toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
fun parseLongitude(text: String): Double? = text.toDoubleOrNull()?.takeIf { it in -180.0..180.0 }

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val current: String) : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(val info: ReleaseInfo, val progress: Float) : UpdateState
    data class ReadyToInstall(val file: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val isRunning: StateFlow<Boolean> = MockLocationService.isRunning
    val progress: StateFlow<Float> = MockLocationService.progress
    val currentPoint: StateFlow<TrackPoint?> = MockLocationService.currentPoint
    val distanceTraveled: StateFlow<Double> = MockLocationService.distanceTraveled

    private val prefs = app.getSharedPreferences("locomocktion", Context.MODE_PRIVATE)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    init {
        val savedMode = prefs.getString("travel_mode", null)
        val savedUnits = prefs.getString("unit_system", null)
        _uiState.value = UiState(
            speedKmh = prefs.getFloat("speed_kmh", 20f),
            customMode = prefs.getBoolean("custom_mode", false),
            updateIntervalMs = prefs.getLong("update_interval_ms", 1000L),
            travelMode = TravelMode.entries.firstOrNull { it.name == savedMode } ?: TravelMode.Normal,
            useGpxSpeed = prefs.getBoolean("use_gpx_speed", false),
            unitSystem = UnitSystem.entries.firstOrNull { it.name == savedUnits } ?: UnitSystem.Metric,
            useFixedLocation = prefs.getBoolean("use_fixed_location", false),
            fixedLat = prefs.getString("fixed_lat", "") ?: "",
            fixedLon = prefs.getString("fixed_lon", "") ?: "",
        )

        val lastUri = prefs.getString("last_gpx_uri", null)
        if (lastUri != null) {
            loadGpx(
                Uri.parse(lastUri),
                prefs.getString("last_gpx_name", null),
                autoSelectIndex = prefs.getInt("last_gpx_track_index", -1),
            )
        }
    }

    /** Loads [uri]; [displayName] is looked up from the content provider when null. */
    fun loadGpx(uri: Uri, displayName: String?, autoSelectIndex: Int = -1) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val name = displayName ?: queryDisplayName(uri)
                val stream = app.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Cannot open file")
                val allTracks = stream.use { parseGpx(it) }
                    .filter { it.points.isNotEmpty() }

                if (allTracks.isEmpty()) {
                    _uiState.update { it.copy(isLoading = false, error = "GPX file contains no tracks") }
                    return@launch
                }

                val index = if (allTracks.size == 1) 0 else autoSelectIndex
                if (index in allTracks.indices) {
                    applyTrack(allTracks[index], index, uri, name, allTracks)
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            availableTracks = allTracks,
                            showTrackSelector = true,
                            pendingUri = uri.toString(),
                            pendingDisplayName = name,
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to parse GPX: ${e.message}") }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? =
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }

    fun selectTrack(index: Int) {
        val state = _uiState.value
        val tracks = state.availableTracks ?: return
        val track = tracks.getOrNull(index) ?: return
        val uri = state.pendingUri?.let { Uri.parse(it) }
        val name = state.pendingDisplayName
        _uiState.update {
            it.copy(showTrackSelector = false, isLoading = true)
        }
        viewModelScope.launch(Dispatchers.IO) {
            applyTrack(track, index, uri, name, tracks)
        }
    }

    fun showTrackSelection() {
        if (_uiState.value.hasMultipleTracks) {
            _uiState.update { it.copy(showTrackSelector = true) }
        }
    }

    fun dismissTrackSelection() {
        _uiState.update { it.copy(showTrackSelector = false) }
    }

    private fun applyTrack(
        raw: GpxTrack,
        trackIndex: Int,
        uri: Uri?,
        displayName: String?,
        allTracks: List<GpxTrack>,
    ) {
        val name = displayName ?: raw.name ?: "Unknown"
        val maxKm = raw.totalDistanceKm.toFloat()

        // Restore saved start/end positions when loading the same track
        val savedUri = prefs.getString("last_gpx_uri", null)
        val savedTrackIndex = prefs.getInt("last_gpx_track_index", -1)
        val isSameTrack = uri?.toString() == savedUri && trackIndex == savedTrackIndex
        val startKm = if (isSameTrack) prefs.getFloat("start_km", 0f).coerceIn(0f, maxKm) else 0f
        val endKm = if (isSameTrack) prefs.getFloat("end_km", maxKm).coerceIn(0f, maxKm) else maxKm

        _uiState.update {
            it.copy(
                track = raw,
                fileName = name,
                startKm = startKm,
                endKm = endKm,
                error = null,
                isLoading = false,
                availableTracks = allTracks,
                pendingUri = uri?.toString(),
                pendingDisplayName = displayName,
            )
        }
        if (uri != null) {
            prefs.edit()
                .putString("last_gpx_uri", uri.toString())
                .putString("last_gpx_name", name)
                .putInt("last_gpx_track_index", trackIndex)
                .apply()
        }
    }

    /** Applies a settings change and writes every scalar setting, so no field can be left unpersisted. */
    private fun updateSettings(transform: (UiState) -> UiState) {
        val state = _uiState.updateAndGet(transform)
        prefs.edit()
            .putFloat("speed_kmh", state.speedKmh)
            .putBoolean("custom_mode", state.customMode)
            .putLong("update_interval_ms", state.updateIntervalMs)
            .putString("travel_mode", state.travelMode.name)
            .putBoolean("use_gpx_speed", state.useGpxSpeed)
            .putString("unit_system", state.unitSystem.name)
            .putBoolean("use_fixed_location", state.useFixedLocation)
            .putString("fixed_lat", state.fixedLat)
            .putString("fixed_lon", state.fixedLon)
            .apply()
    }

    fun setSpeed(kmh: Float) {
        updateSettings { it.copy(speedKmh = kmh) }
        MockLocationService.updateSpeed(kmh / 3.6f)
    }

    fun setCustomMode(custom: Boolean) = updateSettings { it.copy(customMode = custom) }

    fun setUseGpxSpeed(enabled: Boolean) {
        updateSettings { it.copy(useGpxSpeed = enabled) }
        MockLocationService.updateUseGpxSpeed(enabled)
    }

    fun setUpdateInterval(ms: Long) {
        updateSettings { it.copy(updateIntervalMs = ms) }
        MockLocationService.updateInterval(ms)
    }

    fun setStartKm(km: Float) {
        val track = _uiState.value.track ?: return
        val clamped = km.coerceIn(0f, track.totalDistanceKm.toFloat())
        _uiState.update { it.copy(startKm = clamped) }
        prefs.edit().putFloat("start_km", clamped).apply()
    }

    fun setEndKm(km: Float) {
        val track = _uiState.value.track ?: return
        val clamped = km.coerceIn(0f, track.totalDistanceKm.toFloat())
        _uiState.update { it.copy(endKm = clamped) }
        prefs.edit().putFloat("end_km", clamped).apply()
    }

    fun setTravelMode(mode: TravelMode) = updateSettings { it.copy(travelMode = mode) }

    fun setUnitSystem(system: UnitSystem) = updateSettings { it.copy(unitSystem = system) }

    fun setUseFixedLocation(enabled: Boolean) = updateSettings { it.copy(useFixedLocation = enabled) }

    fun setFixedLat(lat: String) = updateSettings { it.copy(fixedLat = lat) }

    fun setFixedLon(lon: String) = updateSettings { it.copy(fixedLon = lon) }

    fun checkForUpdate() {
        if (_updateState.value is UpdateState.Checking ||
            _updateState.value is UpdateState.Downloading
        ) return
        _updateState.value = UpdateState.Checking
        viewModelScope.launch {
            try {
                val release = fetchLatestRelease()
                val current = BuildConfig.VERSION_NAME
                _updateState.value = if (isNewerVersion(release.tagName, current)) {
                    UpdateState.Available(release)
                } else {
                    UpdateState.UpToDate(current)
                }
            } catch (e: UnknownHostException) {
                _updateState.value = UpdateState.Error("No internet connection")
            } catch (e: IOException) {
                _updateState.value = UpdateState.Error("Network error: ${e.message ?: "failed"}")
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.message ?: "Update check failed")
            }
        }
    }

    fun startDownload(info: ReleaseInfo) {
        val url = info.apkDownloadUrl
        if (url.isNullOrEmpty()) {
            _updateState.value = UpdateState.Error("Release has no APK asset")
            return
        }
        _updateState.value = UpdateState.Downloading(info, 0f)
        viewModelScope.launch {
            try {
                val file = downloadApk(app, url) { progress ->
                    _updateState.value = UpdateState.Downloading(info, progress)
                }
                _updateState.value = UpdateState.ReadyToInstall(file)
            } catch (e: IOException) {
                _updateState.value = UpdateState.Error("Download failed: ${e.message ?: "I/O error"}")
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.message ?: "Download failed")
            }
        }
    }

    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }

    fun startMocking() {
        val state = _uiState.value

        if (state.useFixedLocation) {
            val point = state.fixedPoint ?: return
            MockLocationService.configureFixed(point = point, intervalMs = state.updateIntervalMs)
        } else {
            val track = state.track ?: return
            MockLocationService.configure(
                track = track,
                speed = state.speedKmh / 3.6f,
                useGpxSpeed = state.useGpxSpeed,
                intervalMs = state.updateIntervalMs,
                offsetMeters = (state.startKm * 1000).toDouble(),
                endOffsetMeters = (state.endKm * 1000).toDouble(),
                travelMode = state.travelMode,
            )
        }

        app.startForegroundService(Intent(app, MockLocationService::class.java))
    }

    fun stopMocking() {
        val intent = Intent(app, MockLocationService::class.java).apply { action = MockLocationService.ACTION_STOP }
        app.startService(intent)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
