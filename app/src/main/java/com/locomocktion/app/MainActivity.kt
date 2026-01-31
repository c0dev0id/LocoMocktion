package com.locomocktion.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.locomocktion.app.databinding.ActivityMainBinding
import io.jenetics.jpx.GPX
import io.jenetics.jpx.Track
import io.jenetics.jpx.TrackSegment
import io.jenetics.jpx.WayPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline
import java.io.InputStream

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var routePoints: List<WayPoint> = emptyList()
    private var mockService: LocationMockService? = null
    private var isPlaying = false
    private val REQUEST_CODE_PICK_GPX = 1
    private val REQUEST_CODE_PERMISSIONS = 100

    private val speeds = listOf(5, 10, 20, 30, 40, 50, 60, 80, 100, 120)
    private var selectedSpeed = 30 // Default speed in km/h

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize OSMDroid configuration
        Configuration.getInstance().load(this, getPreferences(MODE_PRIVATE))
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupMap()
        setupSpeedSpinner()
        setupButtons()
        requestPermissions()
    }

    private fun setupMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.controller.setZoom(15.0)
        
        // Default location (can be changed)
        val startPoint = GeoPoint(48.8566, 2.3522) // Paris
        binding.mapView.controller.setCenter(startPoint)
    }

    private fun setupSpeedSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, speeds.map { "$it km/h" })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.speedSpinner.adapter = adapter
        
        // Set default to 30 km/h
        binding.speedSpinner.setSelection(speeds.indexOf(30))
        
        binding.speedSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                selectedSpeed = speeds[position]
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        binding.uploadButton.setOnClickListener {
            openGpxFilePicker()
        }

        binding.playButton.setOnClickListener {
            if (!isPlaying) {
                startMocking()
            } else {
                pauseMocking()
            }
        }

        binding.stopButton.setOnClickListener {
            stopMocking()
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun openGpxFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/gpx+xml", "application/xml", "text/xml"))
        }
        startActivityForResult(intent, REQUEST_CODE_PICK_GPX)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_PICK_GPX && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    val inputStream: InputStream? = contentResolver.openInputStream(uri)
                    inputStream?.let {
                        loadGpxFile(it)
                        it.close()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.error_reading_gpx), Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }
        }
    }

    private fun loadGpxFile(inputStream: InputStream) {
        try {
            val gpx = GPX.read(inputStream)
            
            // Extract waypoints from tracks or routes
            val points = mutableListOf<WayPoint>()
            
            // Try tracks first
            gpx.tracks().forEach { track: Track ->
                track.segments().forEach { segment: TrackSegment ->
                    segment.points.forEach { point ->
                        points.add(point)
                    }
                }
            }
            
            // If no tracks, try routes
            if (points.isEmpty()) {
                gpx.routes().forEach { route ->
                    route.points.forEach { point ->
                        points.add(point)
                    }
                }
            }

            if (points.isNotEmpty()) {
                routePoints = points
                displayRouteOnMap()
                binding.statusText.text = getString(R.string.route_loaded, points.size)
                binding.playButton.isEnabled = true
                Toast.makeText(this, "GPX loaded: ${points.size} points", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No route data found in GPX", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_reading_gpx), Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun displayRouteOnMap() {
        // Clear existing overlays
        binding.mapView.overlays.clear()

        if (routePoints.isEmpty()) return

        // Create polyline for route
        val polyline = Polyline().apply {
            outlinePaint.color = Color.BLUE
            outlinePaint.strokeWidth = 8f
        }

        val geoPoints = routePoints.map { wp ->
            GeoPoint(wp.latitude.toDouble(), wp.longitude.toDouble())
        }

        polyline.setPoints(geoPoints)
        binding.mapView.overlays.add(polyline)

        // Center map on route
        if (geoPoints.isNotEmpty()) {
            binding.mapView.controller.setCenter(geoPoints[0])
            
            // Calculate bounds to fit the entire route
            val boundingBox = org.osmdroid.util.BoundingBox.fromGeoPoints(geoPoints)
            binding.mapView.post {
                binding.mapView.zoomToBoundingBox(boundingBox, true, 100)
            }
        }

        binding.mapView.invalidate()
    }

    private fun startMocking() {
        if (routePoints.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_route_loaded), Toast.LENGTH_SHORT).show()
            return
        }

        // Check if mock location is enabled
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        try {
            locationManager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false, false, false, false, false,
                true, true, 1, 1
            )
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
        } catch (e: SecurityException) {
            Toast.makeText(this, getString(R.string.error_mock_location), Toast.LENGTH_LONG).show()
            return
        }

        isPlaying = true
        binding.playButton.text = getString(R.string.pause)
        binding.stopButton.isEnabled = true
        binding.uploadButton.isEnabled = false

        // Start the service
        val intent = Intent(this, LocationMockService::class.java).apply {
            putExtra("speed", selectedSpeed)
            putExtra("points", ArrayList(routePoints.map { 
                doubleArrayOf(it.latitude.toDouble(), it.longitude.toDouble()) 
            }))
        }
        startService(intent)

        Toast.makeText(this, getString(R.string.mocking_started), Toast.LENGTH_SHORT).show()
    }

    private fun pauseMocking() {
        isPlaying = false
        binding.playButton.text = getString(R.string.play)
        
        val intent = Intent(this, LocationMockService::class.java)
        intent.action = "PAUSE"
        startService(intent)
    }

    private fun stopMocking() {
        isPlaying = false
        binding.playButton.text = getString(R.string.play)
        binding.stopButton.isEnabled = false
        binding.uploadButton.isEnabled = true

        val intent = Intent(this, LocationMockService::class.java)
        stopService(intent)

        // Remove test provider
        try {
            val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        Toast.makeText(this, getString(R.string.mocking_stopped), Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMocking()
    }
}
