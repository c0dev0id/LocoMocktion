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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UiState(
    val track: GpxTrack? = null,
    val fileName: String? = null,
    val speedKmh: Float = 20f,
    val useGpxSpeed: Boolean = false,
    val updateIntervalMs: Long = 1000L,
    val startKm: Float = 0f,
    val endKm: Float = 0f,
    val travelMode: TravelMode = TravelMode.Normal,
    val error: String? = null,
    val isLoading: Boolean = false,
    val availableTracks: List<GpxTrack>? = null,
    val showTrackSelector: Boolean = false,
    val pendingUri: String? = null,
    val pendingDisplayName: String? = null,
)

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val isRunning: StateFlow<Boolean> = MockLocationService.isRunning
    val progress: StateFlow<Float> = MockLocationService.progress
    val currentPoint: StateFlow<TrackPoint?> = MockLocationService.currentPoint
    val distanceTraveled: StateFlow<Double> = MockLocationService.distanceTraveled

    private val prefs = app.getSharedPreferences("locomocktion", Context.MODE_PRIVATE)

    init {
        // Restore persisted settings before loading the track
        val savedSpeed = prefs.getFloat("speed_kmh", 20f)
        val savedInterval = prefs.getLong("update_interval_ms", 1000L)
        val savedMode = try {
            TravelMode.valueOf(prefs.getString("travel_mode", TravelMode.Normal.name)!!)
        } catch (e: IllegalArgumentException) {
            TravelMode.Normal
        }
        val savedUseGpxSpeed = prefs.getBoolean("use_gpx_speed", false)
        _uiState.update {
            it.copy(speedKmh = savedSpeed, updateIntervalMs = savedInterval, travelMode = savedMode, useGpxSpeed = savedUseGpxSpeed)
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
