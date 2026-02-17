package com.locomocktion

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.locomocktion.gpx.GpxTrack
import com.locomocktion.gpx.TrackPoint
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
)

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val isRunning: StateFlow<Boolean> = MockLocationService.isRunning
    val progress: StateFlow<Float> = MockLocationService.progress
    val currentPoint: StateFlow<TrackPoint?> = MockLocationService.currentPoint
    val distanceTraveled: StateFlow<Double> = MockLocationService.distanceTraveled

    fun loadGpx(uri: Uri, displayName: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stream = app.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Cannot open file")
                val track = stream.use { parseGpx(it) }
                if (track.points.isEmpty()) {
                    _uiState.update { it.copy(error = "GPX file contains no track points") }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        track = track,
                        fileName = displayName ?: track.name ?: "Unknown",
                        startKm = 0f,
                        endKm = (track.totalDistanceMeters / 1000).toFloat(),
                        error = null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to parse GPX: ${e.message}") }
            }
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
        _uiState.update {
            it.copy(startKm = km.coerceIn(0f, it.endKm.coerceAtMost(maxKm)))
        }
    }

    fun setEndKm(km: Float) {
        val track = _uiState.value.track ?: return
        val maxKm = (track.totalDistanceMeters / 1000).toFloat()
        _uiState.update {
            it.copy(endKm = km.coerceIn(it.startKm.coerceAtLeast(0f), maxKm))
        }
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
