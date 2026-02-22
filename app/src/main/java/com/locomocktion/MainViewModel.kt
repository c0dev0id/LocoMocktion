package com.locomocktion

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.locomocktion.gpx.GpxTrack
import com.locomocktion.gpx.TrackPoint
import com.locomocktion.gpx.distanceBetween
import com.locomocktion.gpx.parseGpx
import com.locomocktion.service.MockLocationService
import com.locomocktion.service.TravelMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UiState(
    val track: GpxTrack? = null,
    val fileName: String? = null,
    val speedKmh: Float = 20f,
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
       _uiState.update {
           it.copy(
               track = raw,
               fileName = name,
               startKm = 0f,
               endKm = (raw.totalDistanceMeters / 1000).toFloat(),
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
    }

    fun setUpdateInterval(ms: Long) {
        _uiState.update { it.copy(updateIntervalMs = ms) }
        MockLocationService.updateInterval(ms)
    }

    fun setStartKm(km: Float) {
        val track = _uiState.value.track ?: return
        val maxKm = (track.totalDistanceMeters / 1000).toFloat()
        _uiState.update { it.copy(startKm = km.coerceIn(0f, maxKm)) }
    }

    fun setEndKm(km: Float) {
        val track = _uiState.value.track ?: return
        val maxKm = (track.totalDistanceMeters / 1000).toFloat()
        _uiState.update { it.copy(endKm = km.coerceIn(0f, maxKm)) }
    }

    fun setTravelMode(mode: TravelMode) {
        _uiState.update { it.copy(travelMode = mode) }
    }

   fun startMocking() {
       val state = _uiState.value
       val track = state.track ?: return
       
       MockLocationService.configure(
           points = track.points,
           speed = state.speedKmh / 3.6f,
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
