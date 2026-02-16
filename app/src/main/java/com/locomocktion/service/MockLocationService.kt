package com.locomocktion.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.locomocktion.MainActivity
import com.locomocktion.R
import com.locomocktion.gpx.TrackPoint
import com.locomocktion.gpx.distanceBetween
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MockLocationService : LifecycleService() {

    companion object {
        private const val CHANNEL_ID = "mock_location_channel"
        private const val NOTIFICATION_ID = 1
        private const val PROVIDER = LocationManager.GPS_PROVIDER

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        private val _progress = MutableStateFlow(0f)
        val progress: StateFlow<Float> = _progress

        private val _currentPoint = MutableStateFlow<TrackPoint?>(null)
        val currentPoint: StateFlow<TrackPoint?> = _currentPoint

        private val _distanceTraveled = MutableStateFlow(0.0)
        val distanceTraveled: StateFlow<Double> = _distanceTraveled

        private var trackPoints: List<TrackPoint> = emptyList()
        private var speedMultiplier: Float = 1f
        var updateIntervalMs: Long = 1000L
            private set
        private var startOffsetMeters: Double = 0.0

        fun configure(
            points: List<TrackPoint>,
            speed: Float,
            intervalMs: Long,
            offsetMeters: Double,
        ) {
            trackPoints = points
            speedMultiplier = speed
            updateIntervalMs = intervalMs
            startOffsetMeters = offsetMeters
        }

        fun updateSpeed(speed: Float) {
            speedMultiplier = speed
        }

        fun updateInterval(ms: Long) {
            updateIntervalMs = ms
        }
    }

    private var mockJob: Job? = null
    private lateinit var locationManager: LocationManager

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            "STOP" -> {
                stopMocking()
                return START_NOT_STICKY
            }
        }

        if (trackPoints.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        startMocking()

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        stopMocking()
        super.onDestroy()
    }

    private fun startMocking() {
        if (mockJob?.isActive == true) return

        try {
            try {
                locationManager.removeTestProvider(PROVIDER)
            } catch (_: Exception) {}

            locationManager.addTestProvider(
                PROVIDER,
                false, false, false, false, true, true, true,
                ProviderProperties.POWER_USAGE_LOW,
                ProviderProperties.ACCURACY_FINE,
            )
            locationManager.setTestProviderEnabled(PROVIDER, true)
        } catch (e: SecurityException) {
            stopSelf()
            return
        }

        _isRunning.value = true

        mockJob = lifecycleScope.launch(Dispatchers.Default) {
            walkTrack()
            withContext(Dispatchers.Main) { stopMocking() }
        }
    }

    private suspend fun walkTrack() {
        if (trackPoints.size < 2) return

        // Pre-compute cumulative distances for each segment boundary
        val segmentDistances = (0 until trackPoints.lastIndex).map { i ->
            distanceBetween(trackPoints[i], trackPoints[i + 1])
        }
        val totalDist = segmentDistances.sum()
        if (totalDist < 0.1) return

        // Find starting position from offset
        var segmentIndex = 0
        var segmentOffset = 0.0
        var cumulativeDist = 0.0

        if (startOffsetMeters > 0.0) {
            var remaining = startOffsetMeters.coerceAtMost(totalDist)
            for (i in segmentDistances.indices) {
                if (remaining <= segmentDistances[i]) {
                    segmentIndex = i
                    segmentOffset = remaining
                    break
                }
                remaining -= segmentDistances[i]
                cumulativeDist += segmentDistances[i]
                if (i == segmentDistances.lastIndex) {
                    segmentIndex = i
                    segmentOffset = segmentDistances[i]
                }
            }
            cumulativeDist += segmentOffset
        }

        _distanceTraveled.value = cumulativeDist

        // Walk along each segment, interpolating positions at the configured interval
        while (segmentIndex < trackPoints.size - 1) {
            val from = trackPoints[segmentIndex]
            val to = trackPoints[segmentIndex + 1]
            val segmentDist = segmentDistances[segmentIndex]

            if (segmentDist < 0.1) {
                segmentIndex++
                continue
            }

            // Read current settings each tick so live changes take effect
            val intervalMs = updateIntervalMs
            val metersPerSecond = speedMultiplier.toDouble()
            val stepMeters = metersPerSecond * (intervalMs / 1000.0)

            // Interpolate position
            val fraction = (segmentOffset / segmentDist).coerceIn(0.0, 1.0)
            val lat = from.latitude + (to.latitude - from.latitude) * fraction
            val lon = from.longitude + (to.longitude - from.longitude) * fraction
            val ele = if (from.elevation != null && to.elevation != null) {
                from.elevation + (to.elevation - from.elevation) * fraction
            } else {
                from.elevation ?: to.elevation
            }

            val point = TrackPoint(lat, lon, ele)
            setMockLocation(point, bearing(from, to).toFloat())
            _currentPoint.value = point
            _progress.value = (cumulativeDist / totalDist).toFloat().coerceIn(0f, 1f)
            _distanceTraveled.value = cumulativeDist

            delay(intervalMs)

            segmentOffset += stepMeters
            cumulativeDist += stepMeters

            // Advance to next segment(s) if we've passed the current one
            while (segmentIndex < trackPoints.size - 1 && segmentOffset >= segmentDistances[segmentIndex]) {
                segmentOffset -= segmentDistances[segmentIndex]
                segmentIndex++
            }
        }

        // Set final point
        trackPoints.lastOrNull()?.let { last ->
            setMockLocation(last, 0f)
            _currentPoint.value = last
        }
        _progress.value = 1f
        _distanceTraveled.value = totalDist
    }

    private fun setMockLocation(point: TrackPoint, bearing: Float) {
        try {
            val location = Location(PROVIDER).apply {
                latitude = point.latitude
                longitude = point.longitude
                point.elevation?.let { altitude = it }
                accuracy = 3.0f
                this.bearing = bearing
                speed = speedMultiplier
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            locationManager.setTestProviderLocation(PROVIDER, location)
        } catch (_: Exception) {}
    }

    private fun bearing(from: TrackPoint, to: TrackPoint): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val y = kotlin.math.sin(dLon) * kotlin.math.cos(lat2)
        val x = kotlin.math.cos(lat1) * kotlin.math.sin(lat2) -
                kotlin.math.sin(lat1) * kotlin.math.cos(lat2) * kotlin.math.cos(dLon)
        return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360) % 360
    }

    private fun stopMocking() {
        mockJob?.cancel()
        mockJob = null
        _isRunning.value = false
        _progress.value = 0f
        _currentPoint.value = null
        _distanceTraveled.value = 0.0
        try {
            locationManager.setTestProviderEnabled(PROVIDER, false)
            locationManager.removeTestProvider(PROVIDER)
        } catch (_: Exception) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mock Location",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows when location mocking is active"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MockLocationService::class.java).apply { action = "STOP" },
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LocoMocktion")
            .setContentText("Mocking location along track…")
            .setSmallIcon(R.drawable.ic_location)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopIntent)
            .build()
    }
}
