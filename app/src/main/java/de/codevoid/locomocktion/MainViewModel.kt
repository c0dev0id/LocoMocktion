package de.codevoid.locomocktion

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.codevoid.locomocktion.gpx.GpxTrack
import de.codevoid.locomocktion.gpx.TrackPoint
import de.codevoid.locomocktion.gpx.distanceBetween
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
)

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
        // Restore persisted settings before loading the track
        val savedSpeed = prefs.getFloat("speed_kmh", 20f)
        val savedCustomMode = prefs.getBoolean("custom_mode", false)
        val savedInterval = prefs.getLong("update_interval_ms", 1000L)
        val savedMode = try {
            TravelMode.valueOf(prefs.getString("travel_mode", TravelMode.Normal.name)!!)
        } catch (e: IllegalArgumentException) {
            TravelMode.Normal
        }
        val savedUseGpxSpeed = prefs.getBoolean("use_gpx_speed", false)
        val savedUnits = try {
            UnitSystem.valueOf(prefs.getString("unit_system", UnitSystem.Metric.name)!!)
        } catch (e: IllegalArgumentException) {
            UnitSystem.Metric
        }
        _uiState.update {
            it.copy(
                speedKmh = savedSpeed,
                customMode = savedCustomMode,
                updateIntervalMs = savedInterval,
                travelMode = savedMode,
                useGpxSpeed = savedUseGpxSpeed,
                unitSystem = savedUnits,
            )
        }

        val lastUri = prefs.getString("last_gpx_uri", null)
        val lastName = prefs.getString("last_gpx_name", null)
        val lastTrackIndex = prefs.getInt("last_gpx_track_index", -1)
        if (lastUri != null) {
            loadGpx(Uri.parse(lastUri), lastName, autoSelectIndex = lastTrackIndex)
        }
    }

    fun loadGpx(uri: Uri, displayName: String?, autoSelectIndex: Int = -1) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val stream = app.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Cannot open file")
                val allTracks = stream.use { parseGpx(it) }
                    .filter { it.points.isNotEmpty() }

                if (allTracks.isEmpty()) {
                    _uiState.update { it.copy(isLoading = false, error = "GPX file contains no tracks") }
                    return@launch
                }

                if (allTracks.size == 1) {
                    applyTrack(allTracks[0], 0, uri, displayName, allTracks)
                } else if (autoSelectIndex in allTracks.indices) {
                    applyTrack(allTracks[autoSelectIndex], autoSelectIndex, uri, displayName, allTracks)
                } else {
                    // Multiple tracks – let the user choose
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            availableTracks = allTracks,
                            showTrackSelector = true,
                            pendingUri = uri.toString(),
                            pendingDisplayName = displayName,
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to parse GPX: ${e.message}") }
            }
        }
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
        val state = _uiState.value
        if (state.availableTracks != null && state.availableTracks.size > 1) {
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
       if (raw.points.isEmpty()) {
           _uiState.update { it.copy(isLoading = false, error = "Track contains no points") }
           return
       }
       val name = displayName ?: raw.name ?: "Unknown"
       val maxKm = (raw.totalDistanceMeters / 1000).toFloat()

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

    fun setSpeed(kmh: Float) {
        _uiState.update { it.copy(speedKmh = kmh) }
        MockLocationService.updateSpeed(kmh / 3.6f)
        prefs.edit().putFloat("speed_kmh", kmh).apply()
    }

    fun setCustomMode(custom: Boolean) {
        _uiState.update { it.copy(customMode = custom) }
        prefs.edit().putBoolean("custom_mode", custom).apply()
    }

    fun setUseGpxSpeed(enabled: Boolean) {
        _uiState.update { it.copy(useGpxSpeed = enabled) }
        MockLocationService.updateUseGpxSpeed(enabled)
        prefs.edit().putBoolean("use_gpx_speed", enabled).apply()
    }

    fun setUpdateInterval(ms: Long) {
        _uiState.update { it.copy(updateIntervalMs = ms) }
        MockLocationService.updateInterval(ms)
        prefs.edit().putLong("update_interval_ms", ms).apply()
    }

    fun setStartKm(km: Float) {
        val track = _uiState.value.track ?: return
        val maxKm = (track.totalDistanceMeters / 1000).toFloat()
        val clamped = km.coerceIn(0f, maxKm)
        _uiState.update { it.copy(startKm = clamped) }
        prefs.edit().putFloat("start_km", clamped).apply()
    }

    fun setEndKm(km: Float) {
        val track = _uiState.value.track ?: return
        val maxKm = (track.totalDistanceMeters / 1000).toFloat()
        val clamped = km.coerceIn(0f, maxKm)
        _uiState.update { it.copy(endKm = clamped) }
        prefs.edit().putFloat("end_km", clamped).apply()
    }

    fun setTravelMode(mode: TravelMode) {
        _uiState.update { it.copy(travelMode = mode) }
        prefs.edit().putString("travel_mode", mode.name).apply()
    }

    fun setUnitSystem(system: UnitSystem) {
        _uiState.update { it.copy(unitSystem = system) }
        prefs.edit().putString("unit_system", system.name).apply()
    }

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
       val track = state.track ?: return
       
       MockLocationService.configure(
           points = track.points,
           speed = state.speedKmh / 3.6f,
           useGpxSpeed = state.useGpxSpeed,
           intervalMs = state.updateIntervalMs,
           offsetMeters = (state.startKm * 1000).toDouble(),
           endOffsetMeters = (state.endKm * 1000).toDouble(),
           travelMode = state.travelMode,
       )

       val intent = Intent(app, MockLocationService::class.java)
       app.startForegroundService(intent)
   }

    fun stopMocking() {
        val intent = Intent(app, MockLocationService::class.java).apply { action = "STOP" }
        app.startService(intent)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
