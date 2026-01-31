package com.locomocktion.app

import android.app.Service
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import kotlin.math.*

class LocationMockService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var routePoints: List<DoubleArray> = emptyList()
    private var currentIndex = 0
    private var baseSpeedKmh = 30.0
    private var isPaused = false
    private var isRunning = false
    private lateinit var locationManager: LocationManager

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!isPaused && isRunning && currentIndex < routePoints.size) {
                mockLocation()
                
                if (currentIndex < routePoints.size - 1) {
                    val delay = calculateDelay()
                    handler.postDelayed(this, delay)
                } else {
                    // Reached end of route
                    stopSelf()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                "PAUSE" -> {
                    isPaused = true
                    return START_STICKY
                }
                "RESUME" -> {
                    isPaused = false
                    if (isRunning) {
                        handler.post(updateRunnable)
                    }
                    return START_STICKY
                }
            }

            val speed = it.getIntExtra("speed", 30)
            baseSpeedKmh = speed.toDouble()

            @Suppress("UNCHECKED_CAST")
            val points = it.getSerializableExtra("points") as? ArrayList<DoubleArray>
            points?.let { pts ->
                routePoints = pts
                currentIndex = 0
                isRunning = true
                isPaused = false
                handler.post(updateRunnable)
            }
        }

        return START_STICKY
    }

    private fun mockLocation() {
        if (currentIndex >= routePoints.size) return

        val point = routePoints[currentIndex]
        val location = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = point[0]
            longitude = point[1]
            accuracy = 5f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            
            // Calculate bearing if we have a next point
            if (currentIndex < routePoints.size - 1) {
                val nextPoint = routePoints[currentIndex + 1]
                bearing = calculateBearing(point[0], point[1], nextPoint[0], nextPoint[1]).toFloat()
                speed = (getCurrentSpeed() / 3.6).toFloat() // Convert km/h to m/s
            }
        }

        try {
            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, location)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        currentIndex++
    }

    private fun calculateDelay(): Long {
        if (currentIndex >= routePoints.size - 1) return 1000L

        val currentPoint = routePoints[currentIndex]
        val nextPoint = routePoints[currentIndex + 1]
        
        val distance = calculateDistance(
            currentPoint[0], currentPoint[1],
            nextPoint[0], nextPoint[1]
        )

        val currentSpeed = getCurrentSpeed()
        
        // Calculate time in milliseconds to travel this distance at current speed
        // speed is in km/h, distance is in km
        val timeHours = distance / currentSpeed
        val timeMillis = (timeHours * 3600 * 1000).toLong()
        
        return max(100L, timeMillis) // Minimum 100ms between updates
    }

    private fun getCurrentSpeed(): Double {
        // Adjust speed based on corner angle
        if (currentIndex < 1 || currentIndex >= routePoints.size - 1) {
            return baseSpeedKmh
        }

        val prevPoint = routePoints[currentIndex - 1]
        val currentPoint = routePoints[currentIndex]
        val nextPoint = routePoints[currentIndex + 1]

        val angle = calculateCornerAngle(prevPoint, currentPoint, nextPoint)
        
        // Reduce speed in corners
        // Speed is specified as percentage of base speed
        return when {
            angle < 90 -> baseSpeedKmh * 0.4  // Sharp turn: 40% of base speed
            angle < 120 -> baseSpeedKmh * 0.6 // Medium turn: 60% of base speed
            angle < 150 -> baseSpeedKmh * 0.8 // Slight turn: 80% of base speed
            else -> baseSpeedKmh                // Straight: 100% of base speed (full speed)
        }
    }

    private fun calculateCornerAngle(p1: DoubleArray, p2: DoubleArray, p3: DoubleArray): Double {
        val bearing1 = calculateBearing(p2[0], p2[1], p1[0], p1[1])
        val bearing2 = calculateBearing(p2[0], p2[1], p3[0], p3[1])
        
        var angle = abs(bearing2 - bearing1)
        if (angle > 180) {
            angle = 360 - angle
        }
        
        return angle
    }

    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val lonDiff = Math.toRadians(lon2 - lon1)

        val y = sin(lonDiff) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(lonDiff)
        val bearing = Math.toDegrees(atan2(y, x))

        return (bearing + 360) % 360
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        // Haversine formula to calculate distance in kilometers
        val R = 6371.0 // Earth's radius in kilometers
        
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLat = Math.toRadians(lat2 - lat1)
        val deltaLon = Math.toRadians(lon2 - lon1)

        val a = sin(deltaLat / 2).pow(2) + 
                cos(lat1Rad) * cos(lat2Rad) * sin(deltaLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return R * c
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(updateRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
