package de.codevoid.locomocktion.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import de.codevoid.locomocktion.MainActivity
import de.codevoid.locomocktion.R
import de.codevoid.locomocktion.gpx.TrackPoint
import de.codevoid.locomocktion.gpx.distanceBetween
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

enum class TravelMode(val label: String, val description: String) {
    Normal("Normal", "Travel from Start to End, then stop"),
    Loop("Loop", "Travel from Start to End, then repeat"),
    Bounce("Bounce", "Travel back and forth between Start and End"),
}

class MockLocationService : LifecycleService() {

    companion object {
        private const val CHANNEL_ID = "mock_location_channel"
        private const val NOTIFICATION_ID = 1

        // PASSIVE_PROVIDER cannot be registered as a test provider — it reflects
        // other providers automatically, so it receives mock data for free.
        private val PASSIVE = LocationManager.PASSIVE_PROVIDER

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
        private var useGpxSpeed: Boolean = false
        var updateIntervalMs: Long = 1000L
            private set
        private var startOffsetMeters: Double = 0.0
        private var endOffsetMeters: Double = Double.MAX_VALUE
        private var travelMode: TravelMode = TravelMode.Normal
        private var fixedPoint: TrackPoint? = null

        fun configure(
            points: List<TrackPoint>,
            speed: Float,
            useGpxSpeed: Boolean = false,
            intervalMs: Long,
            offsetMeters: Double,
            endOffsetMeters: Double,
            travelMode: TravelMode,
        ) {
            trackPoints = points
            speedMultiplier = speed
            this.useGpxSpeed = useGpxSpeed
            updateIntervalMs = intervalMs
            startOffsetMeters = offsetMeters
            this.endOffsetMeters = endOffsetMeters
            this.travelMode = travelMode
            fixedPoint = null
        }

        fun configureFixed(point: TrackPoint, intervalMs: Long) {
            fixedPoint = point
            trackPoints = emptyList()
            updateIntervalMs = intervalMs
        }

        fun updateSpeed(speed: Float) {
            speedMultiplier = speed
        }

        fun updateUseGpxSpeed(enabled: Boolean) {
            useGpxSpeed = enabled
        }

        fun updateInterval(ms: Long) {
            updateIntervalMs = ms
        }
    }

    private var mockJob: Job? = null
    private lateinit var locationManager: LocationManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    // Providers successfully registered as test providers for this session.
    private var activeMockProviders: List<String> = emptyList()

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
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

        if (trackPoints.isEmpty() && fixedPoint == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
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

    @SuppressLint("MissingPermission")
    private fun startMocking() {
        if (mockJob?.isActive == true) return

        // Discover all providers at runtime and mock every non-passive one.
        // This covers GPS, network, fused (API 31+), and any OEM-specific providers,
        // preventing navigation apps from falling back to a real unmocked provider.
        val candidates = locationManager.allProviders.filter { it != PASSIVE }
        val succeeded = mutableListOf<String>()
        try {
            for (name in candidates) {
                try { locationManager.removeTestProvider(name) } catch (_: Exception) {}
                try {
                    locationManager.addTestProvider(
                        name,
                        false, false, false, false, true, true, true,
                        ProviderProperties.POWER_USAGE_LOW,
                        ProviderProperties.ACCURACY_FINE,
                    )
                    locationManager.setTestProviderEnabled(name, true)
                    succeeded.add(name)
                } catch (_: Exception) {}
            }
        } catch (e: SecurityException) {
            stopSelf()
            return
        }

        if (succeeded.isEmpty()) {
            stopSelf()
            return
        }

        activeMockProviders = succeeded
        _isRunning.value = true

        mockJob = lifecycleScope.launch {
            // Put FLP itself into mock mode so it stops duty-cycling GPS hardware.
            // Without this, FLP drops and re-requests GPS every ~10 s, and the
            // ~1 s gap leaks the real GPS position to any FLP listener (e.g. DMD2 Next).
            try {
                fusedLocationClient.setMockMode(true).await()
            } catch (_: Exception) {
                // Play Services unavailable — fall back to LocationManager-only mocking.
            }
            val fp = fixedPoint
            if (fp != null) {
                withContext(Dispatchers.Default) { fixedLoop(fp) }
            } else {
                withContext(Dispatchers.Default) { walkTrack() }
            }
            withContext(Dispatchers.Main) { stopMocking() }
        }
    }

    private suspend fun fixedLoop(point: TrackPoint) {
        _currentPoint.value = point
        _progress.value = 1f
        while (isActive) {
            setMockLocation(point, 0f, 0f)
            delay(updateIntervalMs)
        }
    }

    private suspend fun walkTrack() {
        if (trackPoints.size < 2) return

        val segmentDistances = (0 until trackPoints.lastIndex).map { i ->
            distanceBetween(trackPoints[i], trackPoints[i + 1])
        }
        val totalDist = segmentDistances.sum()
        if (totalDist < 0.1) return

        val startMeters = startOffsetMeters.coerceIn(0.0, totalDist)
        val endMeters = endOffsetMeters.coerceIn(0.0, totalDist)
        val rangeMeters = kotlin.math.abs(endMeters - startMeters)
        if (rangeMeters < 0.1) return
        val forward = endMeters >= startMeters

        var totalTraveled = 0.0

        while (true) {
            totalTraveled = walkBetween(
                startMeters, endMeters, segmentDistances,
                rangeMeters, totalTraveled, forward = forward,
            )

            when (travelMode) {
                TravelMode.Normal -> break
                TravelMode.Loop -> delay(3000)
                TravelMode.Bounce -> {
                    delay(3000)
                    totalTraveled = walkBetween(
                        endMeters, startMeters, segmentDistances,
                        rangeMeters, totalTraveled, forward = !forward,
                    )
                    delay(3000)
                }
            }
        }

        // Set final point at end position (Normal mode only)
        val (segIdx, segOff) = findPosition(endMeters, segmentDistances)
        if (segIdx < trackPoints.size - 1) {
            val from = trackPoints[segIdx]
            val to = trackPoints[segIdx + 1]
            val segDist = segmentDistances[segIdx]
            val fraction = if (segDist < 0.1) 0.0 else (segOff / segDist).coerceIn(0.0, 1.0)
            val finalPoint = TrackPoint(
                from.latitude + (to.latitude - from.latitude) * fraction,
                from.longitude + (to.longitude - from.longitude) * fraction,
                if (from.elevation != null && to.elevation != null)
                    from.elevation + (to.elevation - from.elevation) * fraction
                else from.elevation ?: to.elevation,
            )
            setMockLocation(finalPoint, 0f)
            _currentPoint.value = finalPoint
        }
        _progress.value = 1f
        _distanceTraveled.value = totalTraveled
    }

    private suspend fun walkBetween(
        fromMeters: Double,
        toMeters: Double,
        segmentDistances: List<Double>,
        rangeMeters: Double,
        initialTraveled: Double,
        forward: Boolean,
    ): Double {
        var (segmentIndex, segmentOffset) = findPosition(fromMeters, segmentDistances)
        var legTraveled = 0.0
        var totalTraveled = initialTraveled

        while (legTraveled < rangeMeters) {
            if (segmentIndex < 0 || segmentIndex >= segmentDistances.size) break

            val from = trackPoints[segmentIndex]
            val to = trackPoints[segmentIndex + 1]
            val segmentDist = segmentDistances[segmentIndex]

            if (segmentDist < 0.1) {
                if (forward) segmentIndex++ else segmentIndex--
                segmentOffset = 0.0
                continue
            }

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
            val bearingVal = if (forward) bearing(from, to) else bearing(to, from)

            // Determine effective speed: interpolate GPX speed if available and enabled,
            // otherwise fall back to the user-configured speed multiplier.
            val intervalMs = updateIntervalMs
            val metersPerSecond = if (useGpxSpeed && (from.speed != null || to.speed != null)) {
                val fromSpeed = from.speed ?: to.speed!!
                val toSpeed = to.speed ?: from.speed!!
                (fromSpeed + (toSpeed - fromSpeed) * fraction).coerceAtLeast(0.1)
            } else {
                speedMultiplier.toDouble()
            }
            val stepMeters = metersPerSecond * (intervalMs / 1000.0)

            setMockLocation(point, bearingVal.toFloat(), metersPerSecond.toFloat())
            _currentPoint.value = point

            val progressInLeg = (legTraveled / rangeMeters).toFloat().coerceIn(0f, 1f)
            _progress.value = if (forward) progressInLeg else 1f - progressInLeg
            _distanceTraveled.value = totalTraveled

            delay(intervalMs)

            legTraveled += stepMeters
            totalTraveled += stepMeters

            if (forward) {
                segmentOffset += stepMeters
                while (segmentIndex < segmentDistances.size - 1 &&
                    segmentOffset >= segmentDistances[segmentIndex]
                ) {
                    segmentOffset -= segmentDistances[segmentIndex]
                    segmentIndex++
                }
            } else {
                segmentOffset -= stepMeters
                while (segmentIndex > 0 && segmentOffset < 0) {
                    segmentIndex--
                    segmentOffset += segmentDistances[segmentIndex]
                }
                if (segmentOffset < 0) segmentOffset = 0.0
            }
        }

        return totalTraveled
    }

    private fun findPosition(
        offsetMeters: Double,
        segmentDistances: List<Double>,
    ): Pair<Int, Double> {
        var remaining = offsetMeters
        for (i in segmentDistances.indices) {
            if (remaining <= segmentDistances[i]) {
                return Pair(i, remaining)
            }
            remaining -= segmentDistances[i]
        }
        return Pair(segmentDistances.lastIndex, segmentDistances.last())
    }

    @SuppressLint("MissingPermission")
    private fun setMockLocation(point: TrackPoint, bearing: Float, speed: Float = speedMultiplier) {
        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtimeNanos()
        val location = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = point.latitude
            longitude = point.longitude
            point.elevation?.let { altitude = it }
            accuracy = 3.0f
            this.bearing = bearing
            this.speed = speed
            time = now
            elapsedRealtimeNanos = elapsed
        }
        // Push directly into FLP — DMD2 Next receives this without any GPS cycling gaps.
        fusedLocationClient.setMockLocation(location)
        // Also push to individual LocationManager providers for non-FLP apps.
        for (name in activeMockProviders) {
            try {
                locationManager.setTestProviderLocation(name, Location(name).apply {
                    latitude = point.latitude
                    longitude = point.longitude
                    point.elevation?.let { altitude = it }
                    accuracy = 3.0f
                    this.bearing = bearing
                    this.speed = speed
                    time = now
                    elapsedRealtimeNanos = elapsed
                })
            } catch (_: Exception) {}
        }
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
        fixedPoint = null
        fusedLocationClient.setMockMode(false)
        for (name in activeMockProviders) {
            try {
                locationManager.setTestProviderEnabled(name, false)
                locationManager.removeTestProvider(name)
            } catch (_: Exception) {}
        }
        activeMockProviders = emptyList()
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
            .setContentText(if (fixedPoint != null) "Mocking fixed location…" else "Mocking location along track…")
            .setSmallIcon(R.drawable.ic_location)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopIntent)
            .build()
    }
}
