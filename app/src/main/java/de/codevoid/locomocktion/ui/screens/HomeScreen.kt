package de.codevoid.locomocktion.ui.screens

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Typeface
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.codevoid.locomocktion.BuildConfig
import de.codevoid.locomocktion.MainViewModel
import de.codevoid.locomocktion.UiState
import de.codevoid.locomocktion.UpdateState
import de.codevoid.locomocktion.gpx.GpxTrack
import de.codevoid.locomocktion.gpx.TrackPoint
import de.codevoid.locomocktion.gpx.interpolate
import de.codevoid.locomocktion.parseLatitude
import de.codevoid.locomocktion.parseLongitude
import de.codevoid.locomocktion.service.TravelMode
import de.codevoid.locomocktion.ui.UnitSystem
import de.codevoid.locomocktion.ui.displayToKm
import de.codevoid.locomocktion.ui.displayToKmh
import de.codevoid.locomocktion.ui.distanceUnitLabel
import de.codevoid.locomocktion.ui.formatDistanceKm
import de.codevoid.locomocktion.ui.formatSpeed
import de.codevoid.locomocktion.ui.kmToDisplay
import de.codevoid.locomocktion.ui.kmhToDisplay
import de.codevoid.locomocktion.ui.speedUnitLabel
import de.codevoid.locomocktion.updater.ReleaseInfo
import de.codevoid.locomocktion.updater.launchInstaller
import de.codevoid.locomocktion.util.isMockLocationEnabled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val currentPoint by viewModel.currentPoint.collectAsState()
    val distanceTraveled by viewModel.distanceTraveled.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: Exception) {}
        viewModel.loadGpx(uri, null)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var showMockLocationDialog by remember { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val updateState by viewModel.updateState.collectAsState()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    if (showMockLocationDialog) {
        MockLocationPermissionDialog(
            onDismiss = { showMockLocationDialog = false },
            onOpenSettings = {
                showMockLocationDialog = false
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            },
        )
    }

    if (uiState.showTrackSelector) {
        uiState.availableTracks?.let { tracks ->
            TrackSelectionDialog(
                tracks = tracks,
                unitSystem = uiState.unitSystem,
                onSelect = viewModel::selectTrack,
                onDismiss = viewModel::dismissTrackSelection,
            )
        }
    }

    if (settingsOpen) {
        SettingsDialog(
            unitSystem = uiState.unitSystem,
            onUnitSystemChange = viewModel::setUnitSystem,
            onCheckUpdate = viewModel::checkForUpdate,
            onDismiss = { settingsOpen = false },
        )
    }

    if (updateState !is UpdateState.Idle) {
        UpdateDialog(
            state = updateState,
            onDownload = viewModel::startDownload,
            onDismiss = viewModel::dismissUpdate,
        )
    }

    val onPickFile = { filePicker.launch(arrayOf("application/*", "*/*")) }
    val onStart = {
        if (isMockLocationEnabled(context)) {
            viewModel.startMocking()
        } else {
            showMockLocationDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LocoMocktion") },
                actions = {
                    IconButton(onClick = { uriHandler.openUri("https://buymeacoffee.com/codevoid") }) {
                        Text("☕", fontSize = 18.sp)
                    }
                    IconButton(onClick = { settingsOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (isLandscape) {
            LandscapeContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                uiState = uiState,
                isRunning = isRunning,
                progress = progress,
                currentPoint = currentPoint,
                distanceTraveled = distanceTraveled,
                viewModel = viewModel,
                onPickFile = onPickFile,
                onStart = onStart,
                onStop = viewModel::stopMocking,
            )
        } else {
            PortraitContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                uiState = uiState,
                isRunning = isRunning,
                progress = progress,
                currentPoint = currentPoint,
                distanceTraveled = distanceTraveled,
                viewModel = viewModel,
                onPickFile = onPickFile,
                onStart = onStart,
                onStop = viewModel::stopMocking,
            )
        }
    }
}

@Composable
private fun PortraitContent(
    modifier: Modifier,
    uiState: UiState,
    isRunning: Boolean,
    progress: Float,
    currentPoint: TrackPoint?,
    distanceTraveled: Double,
    viewModel: MainViewModel,
    onPickFile: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FixedLocationCard(
            enabled = uiState.useFixedLocation,
            lat = uiState.fixedLat,
            lon = uiState.fixedLon,
            onEnabledChange = viewModel::setUseFixedLocation,
            onLatChange = viewModel::setFixedLat,
            onLonChange = viewModel::setFixedLon,
            isRunning = isRunning,
        )

        if (!uiState.useFixedLocation) {
            FileSelectionCard(
                fileName = uiState.fileName,
                track = uiState.track,
                isRunning = isRunning,
                onPickFile = onPickFile,
                hasMultipleTracks = uiState.hasMultipleTracks,
                onSelectTrack = viewModel::showTrackSelection,
                unitSystem = uiState.unitSystem,
            )

            TrackPreviewCard(
                track = uiState.track,
                startKm = uiState.startKm,
                endKm = uiState.endKm,
                currentPoint = currentPoint,
                onStartChange = viewModel::setStartKm,
                onEndChange = viewModel::setEndKm,
                isRunning = isRunning,
                isLoading = uiState.isLoading,
                unitSystem = uiState.unitSystem,
            )
            TrackRangeCard(
                startKm = uiState.startKm,
                endKm = uiState.endKm,
                totalDistanceKm = uiState.track?.totalDistanceKm ?: 0.0,
                onStartChange = viewModel::setStartKm,
                onEndChange = viewModel::setEndKm,
                isRunning = isRunning || uiState.track == null,
                unitSystem = uiState.unitSystem,
            )
            TravelModeCard(
                travelMode = uiState.travelMode,
                onModeChange = viewModel::setTravelMode,
                isRunning = isRunning || uiState.track == null,
            )
            SpeedControlCard(
                speedKmh = uiState.speedKmh,
                onSpeedChange = viewModel::setSpeed,
                customMode = uiState.customMode,
                onCustomModeChange = viewModel::setCustomMode,
                hasGpxSpeed = uiState.track?.hasSpeedData == true,
                useGpxSpeed = uiState.useGpxSpeed,
                onUseGpxSpeedChange = viewModel::setUseGpxSpeed,
                unitSystem = uiState.unitSystem,
            )
        }

        UpdateRateCard(
            intervalMs = uiState.updateIntervalMs,
            onIntervalChange = viewModel::setUpdateInterval,
        )

        if (isRunning) {
            ProgressCard(
                progress = progress,
                currentPoint = currentPoint,
                distanceTraveledMeters = distanceTraveled,
                unitSystem = uiState.unitSystem,
            )
        }

        MockControlButton(
            isRunning = isRunning,
            canStart = uiState.canStart,
            onStart = onStart,
            onStop = onStop,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LandscapeContent(
    modifier: Modifier,
    uiState: UiState,
    isRunning: Boolean,
    progress: Float,
    currentPoint: TrackPoint?,
    distanceTraveled: Double,
    viewModel: MainViewModel,
    onPickFile: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ===== LEFT PANE: Map + File =====
        Column(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!uiState.useFixedLocation) {
                FileSelectionCard(
                    fileName = uiState.fileName,
                    track = uiState.track,
                    isRunning = isRunning,
                    onPickFile = onPickFile,
                    hasMultipleTracks = uiState.hasMultipleTracks,
                    onSelectTrack = viewModel::showTrackSelection,
                    unitSystem = uiState.unitSystem,
                    isCompact = true,
                )

                TrackPreviewCard(
                    track = uiState.track,
                    startKm = uiState.startKm,
                    endKm = uiState.endKm,
                    currentPoint = currentPoint,
                    onStartChange = viewModel::setStartKm,
                    onEndChange = viewModel::setEndKm,
                    isRunning = isRunning,
                    isLoading = uiState.isLoading,
                    unitSystem = uiState.unitSystem,
                    modifier = Modifier.weight(1f),
                    isCompact = true,
                )

                TrackRangeCard(
                    startKm = uiState.startKm,
                    endKm = uiState.endKm,
                    totalDistanceKm = uiState.track?.totalDistanceKm ?: 0.0,
                    onStartChange = viewModel::setStartKm,
                    onEndChange = viewModel::setEndKm,
                    isRunning = isRunning || uiState.track == null,
                    unitSystem = uiState.unitSystem,
                    isCompact = true,
                )
            }
        }

        // ===== RIGHT PANE: Controls =====
        Column(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Scrollable area for controls
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FixedLocationCard(
                    enabled = uiState.useFixedLocation,
                    lat = uiState.fixedLat,
                    lon = uiState.fixedLon,
                    onEnabledChange = viewModel::setUseFixedLocation,
                    onLatChange = viewModel::setFixedLat,
                    onLonChange = viewModel::setFixedLon,
                    isRunning = isRunning,
                )

                if (!uiState.useFixedLocation) {
                    SpeedControlCard(
                        speedKmh = uiState.speedKmh,
                        onSpeedChange = viewModel::setSpeed,
                        customMode = uiState.customMode,
                        onCustomModeChange = viewModel::setCustomMode,
                        hasGpxSpeed = uiState.track?.hasSpeedData == true,
                        useGpxSpeed = uiState.useGpxSpeed,
                        onUseGpxSpeedChange = viewModel::setUseGpxSpeed,
                        unitSystem = uiState.unitSystem,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TravelModeCard(
                            travelMode = uiState.travelMode,
                            onModeChange = viewModel::setTravelMode,
                            isRunning = isRunning || uiState.track == null,
                            modifier = Modifier.weight(1f),
                            isCompact = true,
                        )
                        UpdateRateCard(
                            intervalMs = uiState.updateIntervalMs,
                            onIntervalChange = viewModel::setUpdateInterval,
                            modifier = Modifier.weight(1f),
                            isCompact = true,
                        )
                    }
                } else {
                    UpdateRateCard(
                        intervalMs = uiState.updateIntervalMs,
                        onIntervalChange = viewModel::setUpdateInterval,
                        modifier = Modifier.fillMaxWidth(),
                        isCompact = true,
                    )
                }

                if (isRunning) {
                    ProgressCard(
                        progress = progress,
                        currentPoint = currentPoint,
                        distanceTraveledMeters = distanceTraveled,
                        unitSystem = uiState.unitSystem,
                    )
                }
            }

            // Start/Stop button pinned at bottom
            MockControlButton(
                isRunning = isRunning,
                canStart = uiState.canStart,
                onStart = onStart,
                onStop = onStop,
            )
        }
    }
}

@Composable
private fun FileSelectionCard(
    fileName: String?,
    track: GpxTrack?,
    isRunning: Boolean,
    onPickFile: () -> Unit,
    hasMultipleTracks: Boolean = false,
    onSelectTrack: () -> Unit = {},
    unitSystem: UnitSystem = UnitSystem.Metric,
    isCompact: Boolean = false,
) {
    if (isCompact) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (fileName != null && track != null) {
                    Icon(
                        Icons.Default.Route,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${track.points.size} pts · ${formatDistanceKm(track.totalDistanceKm, unitSystem, decimals = 1)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                if (hasMultipleTracks) {
                    OutlinedButton(
                        onClick = onSelectTrack,
                        enabled = !isRunning,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tracks", style = MaterialTheme.typography.labelMedium)
                    }
                }
                OutlinedButton(
                    onClick = onPickFile,
                    enabled = !isRunning,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (fileName == null) "Select GPX" else "Change",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    } else {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "GPX Track",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (fileName != null && track != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Route,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = fileName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "${track.points.size} points · " +
                                        formatDistanceKm(track.totalDistanceKm, unitSystem),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onPickFile,
                        enabled = !isRunning,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (fileName == null) "Select GPX file" else "Change file")
                    }
                    if (hasMultipleTracks) {
                        OutlinedButton(
                            onClick = onSelectTrack,
                            enabled = !isRunning,
                        ) {
                            Icon(Icons.Default.List, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tracks")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackPreviewCard(
    track: GpxTrack?,
    startKm: Float,
    endKm: Float,
    currentPoint: TrackPoint?,
    onStartChange: (Float) -> Unit,
    onEndChange: (Float) -> Unit,
    isRunning: Boolean,
    isLoading: Boolean = false,
    unitSystem: UnitSystem = UnitSystem.Metric,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
) {
    if (track == null || track.points.size < 2) {
        Card(modifier = modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(if (isCompact) 8.dp else 16.dp)) {
                if (!isCompact) {
                    Text(
                        text = "Track Preview",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isCompact) Modifier.fillMaxHeight() else Modifier.height(200.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoading) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Loading track…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(
                            "Load a GPX file to preview the track",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        return
    }

    val points = track.points
    val cumulativeDistances = track.cumulativeMeters
    val totalDistMeters = track.totalDistanceMeters

    // Pre-compute geographic bounds for coordinate mapping
    val minLat = remember(track) { points.minOf { it.latitude } }
    val maxLat = remember(track) { points.maxOf { it.latitude } }
    val minLon = remember(track) { points.minOf { it.longitude } }
    val maxLon = remember(track) { points.maxOf { it.longitude } }
    val latRange = remember(track) { (maxLat - minLat).coerceAtLeast(0.0001) }
    val lonRange = remember(track) { (maxLon - minLon).coerceAtLeast(0.0001) }

    // Interpolate a TrackPoint at the given distance along the track
    fun positionAtMeters(meters: Double): TrackPoint {
        val m = meters.coerceIn(0.0, totalDistMeters)
        for (i in 0 until points.size - 1) {
            if (m <= cumulativeDistances[i + 1]) {
                val segStart = cumulativeDistances[i]
                val segLen = cumulativeDistances[i + 1] - segStart
                if (segLen < 0.1) return points[i]
                val f = ((m - segStart) / segLen).coerceIn(0.0, 1.0)
                return interpolate(points[i], points[i + 1], f)
            }
        }
        return points.last()
    }

    // Use rememberUpdatedState so the pointer input closure always sees latest values
    val currentStartKm by rememberUpdatedState(startKm)
    val currentEndKm by rememberUpdatedState(endKm)
    val currentIsRunning by rememberUpdatedState(isRunning)

    // --- Zoom / rotation state ---
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var zoomedToSelection by remember { mutableStateOf(false) }

    // Reset zoom/rotation when track changes
    LaunchedEffect(track) {
        zoomScale = 1f
        panOffset = Offset.Zero
        rotationAngle = 0f
        zoomedToSelection = false
    }

    // Container size in px so we can compute selection zoom
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    var containerHeightPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val paddingPx = with(density) { 16.dp.toPx() }
    val labelPaint = remember { Paint().apply { isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD } }
    val labelBgPaint = remember { Paint().apply { isAntiAlias = true } }

    fun rotateAroundCenter(pos: Offset, angle: Float, canvasW: Float, canvasH: Float): Offset {
        val cx = canvasW / 2f
        val cy = canvasH / 2f
        val cos = kotlin.math.cos(angle.toDouble()).toFloat()
        val sin = kotlin.math.sin(angle.toDouble()).toFloat()
        val dx = pos.x - cx
        val dy = pos.y - cy
        return Offset(cx + dx * cos - dy * sin, cy + dx * sin + dy * cos)
    }

    // Maps a track point to canvas pixels under the current zoom, pan and rotation.
    fun project(p: TrackPoint, canvasW: Float, canvasH: Float): Offset {
        val rawX = ((p.longitude - minLon) / lonRange * (canvasW - 2 * paddingPx) + paddingPx).toFloat()
        val rawY = ((1 - (p.latitude - minLat) / latRange) * (canvasH - 2 * paddingPx) + paddingPx).toFloat()
        val zoomed = Offset(rawX * zoomScale + panOffset.x, rawY * zoomScale + panOffset.y)
        return rotateAroundCenter(zoomed, rotationAngle, canvasW, canvasH)
    }

    fun zoomToSelection() {
        if (containerWidthPx <= 0f || containerHeightPx <= 0f) return
        val w = containerWidthPx - 2 * paddingPx
        val h = containerHeightPx - 2 * paddingPx

        val startMeters = (startKm * 1000).toDouble()
        val endMeters = (endKm * 1000).toDouble()
        val lowM = minOf(startMeters, endMeters)
        val highM = maxOf(startMeters, endMeters)

        // Gather points within the selection
        var selMinLat = Double.MAX_VALUE; var selMaxLat = -Double.MAX_VALUE
        var selMinLon = Double.MAX_VALUE; var selMaxLon = -Double.MAX_VALUE
        for (i in points.indices) {
            val d = cumulativeDistances[i]
            if (d in lowM..highM) {
                selMinLat = minOf(selMinLat, points[i].latitude)
                selMaxLat = maxOf(selMaxLat, points[i].latitude)
                selMinLon = minOf(selMinLon, points[i].longitude)
                selMaxLon = maxOf(selMaxLon, points[i].longitude)
            }
        }
        // Include interpolated start/end
        val pStart = positionAtMeters(lowM)
        val pEnd = positionAtMeters(highM)
        selMinLat = minOf(selMinLat, pStart.latitude, pEnd.latitude)
        selMaxLat = maxOf(selMaxLat, pStart.latitude, pEnd.latitude)
        selMinLon = minOf(selMinLon, pStart.longitude, pEnd.longitude)
        selMaxLon = maxOf(selMaxLon, pStart.longitude, pEnd.longitude)

        val selLatRange = (selMaxLat - selMinLat).coerceAtLeast(0.00001)
        val selLonRange = (selMaxLon - selMinLon).coerceAtLeast(0.00001)

        // How much of the full view does the selection occupy?
        val fracW = (selLonRange / lonRange).toFloat()
        val fracH = (selLatRange / latRange).toFloat()
        val newScale = (0.8f / maxOf(fracW, fracH)).coerceIn(1f, 20f)

        // Centre of the selection in normalised [0..1] coordinates
        val cx = ((selMinLon + selLonRange / 2 - minLon) / lonRange).toFloat()
        val cy = (1f - ((selMinLat + selLatRange / 2 - minLat) / latRange).toFloat())

        // Pan so that the centre of the selection is in the centre of the view
        val panX = -(cx * w * newScale - w / 2 + paddingPx * (newScale - 1f))
        val panY = -(cy * h * newScale - h / 2 + paddingPx * (newScale - 1f))

        zoomScale = newScale
        panOffset = Offset(panX, panY)
        rotationAngle = 0f
    }

    fun resetZoom() {
        zoomScale = 1f
        panOffset = Offset.Zero
        rotationAngle = 0f
    }

    val cardPadding = if (isCompact) 8.dp else 16.dp
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(cardPadding)) {
            // Title row with zoom toggle button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isCompact) {
                    Text(
                        text = track.name ?: "Track Preview",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                IconButton(
                    onClick = {
                        if (zoomedToSelection) {
                            resetZoom()
                            zoomedToSelection = false
                        } else {
                            zoomToSelection()
                            zoomedToSelection = true
                        }
                    },
                    modifier = Modifier.size(if (isCompact) 28.dp else 36.dp),
                ) {
                    Icon(
                        imageVector = if (zoomedToSelection) Icons.Default.ZoomOutMap else Icons.Default.ZoomInMap,
                        contentDescription = if (zoomedToSelection) "View all" else "Zoom to selection",
                        modifier = Modifier.size(if (isCompact) 16.dp else 20.dp),
                    )
                }
            }
            if (!isCompact) Spacer(modifier = Modifier.height(4.dp))

            val trackColor = MaterialTheme.colorScheme.primary

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isCompact) Modifier.fillMaxHeight() else Modifier.height(200.dp))
                    .clipToBounds()
                    .onSizeChanged { size ->
                        containerWidthPx = size.width.toFloat()
                        containerHeightPx = size.height.toFloat()
                    },
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(track) {
                            val canvasW = size.width.toFloat()
                            val canvasH = size.height.toFloat()
                            val hitRadiusPx = 32.dp.toPx()

                            fun toScreen(p: TrackPoint) = project(p, canvasW, canvasH)

                            fun nearestMetersOnTrack(touchPos: Offset): Double {
                                var bestDistSq = Float.MAX_VALUE
                                var bestMeters = 0.0
                                for (i in 0 until points.size - 1) {
                                    val a = toScreen(points[i])
                                    val b = toScreen(points[i + 1])
                                    val abx = b.x - a.x
                                    val aby = b.y - a.y
                                    val abLenSq = abx * abx + aby * aby
                                    val t = if (abLenSq < 0.001f) 0f
                                    else (((touchPos.x - a.x) * abx + (touchPos.y - a.y) * aby) / abLenSq)
                                        .coerceIn(0f, 1f)
                                    val px = a.x + abx * t
                                    val py = a.y + aby * t
                                    val dx = touchPos.x - px
                                    val dy = touchPos.y - py
                                    val distSq = dx * dx + dy * dy
                                    if (distSq < bestDistSq) {
                                        bestDistSq = distSq
                                        bestMeters = cumulativeDistances[i] +
                                                (cumulativeDistances[i + 1] - cumulativeDistances[i]) * t.toDouble()
                                    }
                                }
                                return bestMeters
                            }

                            awaitEachGesture {
                                val down = awaitFirstDown()
                                if (currentIsRunning) return@awaitEachGesture

                                val startPos = toScreen(positionAtMeters((currentStartKm * 1000).toDouble()))
                                val endPos = toScreen(positionAtMeters((currentEndKm * 1000).toDouble()))

                                val dStart = (down.position - startPos).getDistance()
                                val dEnd = (down.position - endPos).getDistance()

                                val target = when {
                                    dStart <= hitRadiusPx && dStart <= dEnd -> "start"
                                    dEnd <= hitRadiusPx -> "end"
                                    else -> null
                                }

                                if (target != null) {
                                    // Marker drag
                                    down.consume()
                                    do {
                                        val event = awaitPointerEvent()
                                        event.changes.forEach { change ->
                                            if (change.pressed) {
                                                change.consume()
                                                val km = (nearestMetersOnTrack(change.position) / 1000).toFloat()
                                                when (target) {
                                                    "start" -> onStartChange(km)
                                                    "end" -> onEndChange(km)
                                                }
                                            }
                                        }
                                    } while (event.changes.any { it.pressed })
                                } else {
                                    // Pinch-to-zoom / two-finger pan / rotation
                                    down.consume()
                                    var prevCentroid = down.position
                                    var prevSpread = 0f
                                    var prevAngle = 0f
                                    var pinching = false
                                    var prevFingerCount = 1
                                    do {
                                        val event = awaitPointerEvent()
                                        val pressed = event.changes.filter { it.pressed }
                                        if (pressed.isEmpty()) break
                                        event.changes.forEach { it.consume() }

                                        val centroid = Offset(
                                            pressed.map { it.position.x }.average().toFloat(),
                                            pressed.map { it.position.y }.average().toFloat(),
                                        )
                                        val fingerCount = pressed.size

                                        if (fingerCount >= 2) {
                                            val sorted = pressed.sortedBy { it.id.value }
                                            val p0 = sorted[0].position
                                            val p1 = sorted[1].position

                                            val spread = pressed.maxOf { (it.position - centroid).getDistance() }
                                            val angle = kotlin.math.atan2(
                                                (p1.y - p0.y).toDouble(),
                                                (p1.x - p0.x).toDouble(),
                                            ).toFloat()

                                            if (pinching && prevSpread > 1f) {
                                                // Zoom around centroid (un-rotated to content space)
                                                val scaleFactor = spread / prevSpread
                                                val newScale = (zoomScale * scaleFactor).coerceIn(1f, 20f)
                                                val contentFocal = rotateAroundCenter(centroid, -rotationAngle, canvasW, canvasH)
                                                panOffset = Offset(
                                                    contentFocal.x - (contentFocal.x - panOffset.x) * (newScale / zoomScale),
                                                    contentFocal.y - (contentFocal.y - panOffset.y) * (newScale / zoomScale),
                                                )
                                                zoomScale = newScale

                                                // Rotation
                                                var angleDelta = angle - prevAngle
                                                while (angleDelta > kotlin.math.PI.toFloat()) angleDelta -= (2 * kotlin.math.PI).toFloat()
                                                while (angleDelta < -kotlin.math.PI.toFloat()) angleDelta += (2 * kotlin.math.PI).toFloat()
                                                rotationAngle += angleDelta
                                            }
                                            prevSpread = spread
                                            prevAngle = angle
                                            pinching = true
                                        }

                                        // Pan — skip when finger count changes to avoid jump
                                        if (fingerCount == prevFingerCount) {
                                            val delta = centroid - prevCentroid
                                            if (zoomScale > 1f || rotationAngle != 0f) {
                                                // Un-rotate screen-space delta to content space
                                                val cos = kotlin.math.cos(-rotationAngle.toDouble()).toFloat()
                                                val sin = kotlin.math.sin(-rotationAngle.toDouble()).toFloat()
                                                panOffset += Offset(
                                                    delta.x * cos - delta.y * sin,
                                                    delta.x * sin + delta.y * cos,
                                                )
                                            }
                                        }

                                        prevCentroid = centroid
                                        prevFingerCount = fingerCount
                                        zoomedToSelection = false
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                        },
                ) {
                    fun toOffset(p: TrackPoint) = project(p, size.width, size.height)

                    // Draw track line: active range in full color, inactive in faded
                    val startMeters = (startKm * 1000).toDouble()
                    val endMeters = (endKm * 1000).toDouble()
                    val lowMeters = minOf(startMeters, endMeters)
                    val highMeters = maxOf(startMeters, endMeters)
                    val rangeMeters = highMeters - lowMeters
                    val isReversed = startMeters > endMeters
                    val inactiveColor = trackColor.copy(alpha = 0.2f)
                    val activeStrokeWidth = 4.dp.toPx()
                    val inactiveStrokeWidth = 2.dp.toPx()

                    for (i in 0 until points.size - 1) {
                        val segStart = cumulativeDistances[i]
                        val segEnd = cumulativeDistances[i + 1]
                        val isActive = segEnd >= lowMeters && segStart <= highMeters
                        drawLine(
                            color = if (isActive) trackColor else inactiveColor,
                            start = toOffset(points[i]),
                            end = toOffset(points[i + 1]),
                            strokeWidth = if (isActive) activeStrokeWidth else inactiveStrokeWidth,
                            cap = StrokeCap.Round,
                        )
                    }

                    // --- Track endpoint bars (perpendicular to track direction) ---
                    val endpointBarLen = 8.dp.toPx()
                    val endpointStroke = 3.dp.toPx()
                    val endpointColor = trackColor.copy(alpha = 0.5f)

                    fun drawEndpointBar(at: Offset, toward: Offset) {
                        val dir = toward - at
                        val len = dir.getDistance()
                        if (len <= 0.1f) return
                        val perp = Offset(-dir.y, dir.x) / len * endpointBarLen
                        drawLine(endpointColor, at + perp, at - perp, strokeWidth = endpointStroke, cap = StrokeCap.Round)
                    }
                    drawEndpointBar(toOffset(points[0]), toOffset(points[1]))
                    drawEndpointBar(toOffset(points.last()), toOffset(points[points.size - 2]))

                    val nativeCanvas = drawContext.canvas.nativeCanvas
                    fun drawLabel(text: String, centerX: Float, baselineY: Float, textColor: Int, textSizePx: Float, bgColor: Int, padX: Float) {
                        labelPaint.color = textColor
                        labelPaint.textSize = textSizePx
                        labelBgPaint.color = bgColor
                        val textW = labelPaint.measureText(text)
                        nativeCanvas.drawRoundRect(
                            centerX - textW / 2 - padX,
                            baselineY - textSizePx,
                            centerX + textW / 2 + padX,
                            baselineY + 3.dp.toPx(),
                            4.dp.toPx(), 4.dp.toPx(), labelBgPaint,
                        )
                        nativeCanvas.drawText(text, centerX - textW / 2, baselineY, labelPaint)
                    }

                    // --- Direction arrows along active segment ---
                    if (rangeMeters > 1.0) {
                        val arrowLen = 8.dp.toPx()
                        val arrowHalfW = 5.dp.toPx()
                        val lookAhead = (rangeMeters * 0.01).coerceAtLeast(50.0)
                        val arrowFractions = listOf(0.25f, 0.5f, 0.75f)
                        for (frac in arrowFractions) {
                            val m = lowMeters + rangeMeters * frac
                            val mAhead = if (isReversed) (m - lookAhead).coerceAtLeast(lowMeters)
                                         else (m + lookAhead).coerceAtMost(highMeters)
                            if (kotlin.math.abs(mAhead - m) < 1.0) continue
                            val pt = toOffset(positionAtMeters(m))
                            val ptAhead = toOffset(positionAtMeters(mAhead))
                            val dir = ptAhead - pt
                            val len = dir.getDistance()
                            if (len < 0.1f) continue
                            val ux = dir.x / len
                            val uy = dir.y / len
                            val px = -uy
                            val py = ux
                            val tipX = pt.x + ux * arrowLen
                            val tipY = pt.y + uy * arrowLen
                            val baseLeftX = pt.x - ux * arrowLen * 0.3f + px * arrowHalfW
                            val baseLeftY = pt.y - uy * arrowLen * 0.3f + py * arrowHalfW
                            val baseRightX = pt.x - ux * arrowLen * 0.3f - px * arrowHalfW
                            val baseRightY = pt.y - uy * arrowLen * 0.3f - py * arrowHalfW
                            val arrowPath = Path().apply {
                                moveTo(tipX, tipY)
                                lineTo(baseLeftX, baseLeftY)
                                lineTo(baseRightX, baseRightY)
                                close()
                            }
                            drawPath(arrowPath, color = Color.White)
                            drawPath(
                                arrowPath,
                                color = trackColor,
                                style = Stroke(width = 1.5f.dp.toPx()),
                            )
                        }

                        // Distance label at midpoint of active range
                        val midPt = toOffset(positionAtMeters(lowMeters + rangeMeters * 0.5))
                        drawLabel(
                            text = formatDistanceKm(rangeMeters / 1000.0, unitSystem, decimals = 1),
                            centerX = midPt.x,
                            baselineY = midPt.y - 10.dp.toPx(),
                            textColor = 0xFF333333.toInt(),
                            textSizePx = 9.sp.toPx(),
                            bgColor = 0xCCFFFFFF.toInt(),
                            padX = 4.dp.toPx(),
                        )
                    }

                    // --- Start/end flag markers (draggable) ---
                    val flagPole = 28.dp.toPx()
                    val flagW = 16.dp.toPx()
                    val flagH = 12.dp.toPx()
                    val cellW = flagW / 2f
                    val cellH = flagH / 2f
                    val poleWidth = 2.5f.dp.toPx()
                    val baseR = 5.dp.toPx()
                    fun drawFlag(pos: Offset, color: Color, flagLeft: Boolean) {
                        val flagX = if (flagLeft) pos.x - flagW else pos.x
                        val flagTop = pos.y - flagPole
                        drawLine(color, start = pos, end = Offset(pos.x, flagTop), strokeWidth = poleWidth, cap = StrokeCap.Round)
                        drawRect(color, topLeft = Offset(flagX, flagTop), size = Size(flagW, flagH))
                        drawRect(Color.White, topLeft = Offset(flagX, flagTop), size = Size(cellW, cellH))
                        drawRect(Color.White, topLeft = Offset(flagX + cellW, flagTop + cellH), size = Size(cellW, cellH))
                        drawCircle(color, radius = baseR, center = pos)
                        drawCircle(Color.White, radius = baseR * 0.5f, center = pos)
                    }

                    val startGreen = Color(0xFF4CAF50)
                    val endBlue = Color(0xFF2196F3)
                    val startPos = toOffset(positionAtMeters(startMeters))
                    val endPos = toOffset(positionAtMeters(endMeters))
                    drawFlag(startPos, startGreen, flagLeft = false)
                    drawFlag(endPos, endBlue, flagLeft = true)

                    // --- Km labels below the flag bases ---
                    val labelTextSize = 10.sp.toPx()
                    val labelOffY = baseR + labelTextSize + 2.dp.toPx()
                    drawLabel(
                        text = formatDistanceKm(startKm.toDouble(), unitSystem, decimals = 1),
                        centerX = startPos.x,
                        baselineY = startPos.y + labelOffY,
                        textColor = 0xFF2E7D32.toInt(),
                        textSizePx = labelTextSize,
                        bgColor = 0xDDFFFFFF.toInt(),
                        padX = 3.dp.toPx(),
                    )
                    drawLabel(
                        text = formatDistanceKm(endKm.toDouble(), unitSystem, decimals = 1),
                        centerX = endPos.x,
                        baselineY = endPos.y + labelOffY,
                        textColor = 0xFF1565C0.toInt(),
                        textSizePx = labelTextSize,
                        bgColor = 0xDDFFFFFF.toInt(),
                        padX = 3.dp.toPx(),
                    )

                    // Red dot: current position (while mocking)
                    currentPoint?.let { cp ->
                        val center = toOffset(cp)
                        drawCircle(color = Color(0xFFF44336), radius = 8.dp.toPx(), center = center)
                        drawCircle(color = Color.White, radius = 4.dp.toPx(), center = center)
                    }
                }

                // Loading overlay when replacing track while one is already shown
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Loading track\u2026",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRangeCard(
    startKm: Float,
    endKm: Float,
    totalDistanceKm: Double,
    onStartChange: (Float) -> Unit,
    onEndChange: (Float) -> Unit,
    isRunning: Boolean,
    unitSystem: UnitSystem = UnitSystem.Metric,
    isCompact: Boolean = false,
) {
    fun formatField(km: Float): String {
        val display = kmToDisplay(km, unitSystem)
        return if (km == 0f) "" else "%.2f".format(display)
    }

    var startText by remember { mutableStateOf(formatField(startKm)) }
    var endText by remember {
        mutableStateOf("%.2f".format(kmToDisplay(endKm, unitSystem)))
    }
    var startFocused by remember { mutableStateOf(false) }
    var endFocused by remember { mutableStateOf(false) }

    LaunchedEffect(startKm, unitSystem) {
        if (!startFocused) startText = formatField(startKm)
    }
    LaunchedEffect(endKm, unitSystem) {
        if (!endFocused) endText = "%.2f".format(kmToDisplay(endKm, unitSystem))
    }

    val cardPadding = if (isCompact) PaddingValues(horizontal = 8.dp, vertical = 4.dp) else PaddingValues(16.dp)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(cardPadding)) {
            if (!isCompact) {
                Text(
                    text = "Track Range",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Track total: ${formatDistanceKm(totalDistanceKm, unitSystem)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            val distLabel = distanceUnitLabel(unitSystem)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = startText,
                    onValueChange = { input ->
                        startText = input
                        val parsed = input.toFloatOrNull()
                        if (parsed != null) onStartChange(displayToKm(parsed, unitSystem))
                        else if (input.isEmpty()) onStartChange(0f)
                    },
                    label = { Text("Start $distLabel") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = !isRunning,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { state ->
                            startFocused = state.isFocused
                            if (!state.isFocused) {
                                startText = formatField(startKm)
                            }
                        },
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { input ->
                        endText = input
                        val parsed = input.toFloatOrNull()
                        if (parsed != null) onEndChange(displayToKm(parsed, unitSystem))
                        else if (input.isEmpty()) onEndChange(totalDistanceKm.toFloat())
                    },
                    label = { Text("End $distLabel") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = !isRunning,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { state ->
                            endFocused = state.isFocused
                            if (!state.isFocused) {
                                endText = "%.2f".format(kmToDisplay(endKm, unitSystem))
                            }
                        },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TravelModeCard(
    travelMode: TravelMode,
    onModeChange: (TravelMode) -> Unit,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
) {
    val cardPadding = if (isCompact) 8.dp else 16.dp
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(cardPadding)) {
            Text(
                text = if (isCompact) "Mode" else "Travel Mode",
                style = if (isCompact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 8.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TravelMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = travelMode == mode,
                        onClick = { onModeChange(mode) },
                        enabled = !isRunning,
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = TravelMode.entries.size,
                        ),
                    ) {
                        Text(mode.label, style = if (isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }

            if (!isCompact) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = travelMode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SpeedControlCard(
    speedKmh: Float,
    onSpeedChange: (Float) -> Unit,
    customMode: Boolean = false,
    onCustomModeChange: (Boolean) -> Unit = {},
    hasGpxSpeed: Boolean = false,
    useGpxSpeed: Boolean = false,
    onUseGpxSpeedChange: (Boolean) -> Unit = {},
    unitSystem: UnitSystem = UnitSystem.Metric,
) {

    // Slider + presets are defined in display units per unit system.
    val sliderMax = when (unitSystem) {
        UnitSystem.Metric -> 150f
        UnitSystem.Imperial -> 90f
    }
    val customSliderPos = sliderMax - 5f
    val presets = when (unitSystem) {
        UnitSystem.Metric -> listOf(
            "Walk" to 5f,
            "Bike" to 20f,
            "Car" to 50f,
            "Fast" to 120f,
        )
        UnitSystem.Imperial -> listOf(
            "Walk" to 3f,
            "Bike" to 12f,
            "Car" to 30f,
            "Fast" to 75f,
        )
    }
    val customDefaultDisplay = when (unitSystem) {
        UnitSystem.Metric -> 400
        UnitSystem.Imperial -> 250
    }
    var customText by rememberSaveable(unitSystem) {
        mutableStateOf(
            if (customMode) kmhToDisplay(speedKmh, unitSystem).toInt().coerceIn(1, 9999).toString()
            else customDefaultDisplay.toString()
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Speed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (useGpxSpeed && hasGpxSpeed) {
                        "GPX speed"
                    } else {
                        formatSpeed(speedKmh, unitSystem)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (hasGpxSpeed) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Use GPX speed data",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "Speed recorded in the GPX file",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = useGpxSpeed,
                        onCheckedChange = onUseGpxSpeedChange,
                    )
                }
            }

            if (!useGpxSpeed || !hasGpxSpeed) {
                Spacer(modifier = Modifier.height(8.dp))

                val sliderDisplayValue = kmhToDisplay(speedKmh, unitSystem).coerceIn(1f, sliderMax)
                Slider(
                    value = if (customMode) customSliderPos else sliderDisplayValue,
                    onValueChange = { newValue ->
                        if (customMode) onCustomModeChange(false)
                        onSpeedChange(displayToKmh(newValue, unitSystem))
                    },
                    valueRange = 1f..sliderMax,
                    steps = 0,
                )

                // Preset speed buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presets.forEach { (label, value) ->
                        FilledTonalButton(
                            onClick = {
                                onCustomModeChange(false)
                                onSpeedChange(displayToKmh(value, unitSystem))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(4.dp),
                        ) {
                            Text(
                                text = "$label\n${value.toInt()}",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = {
                            onCustomModeChange(true)
                            val v = customText.toIntOrNull()?.coerceIn(1, 9999) ?: customDefaultDisplay
                            customText = v.toString()
                            onSpeedChange(displayToKmh(v.toFloat(), unitSystem))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(4.dp),
                    ) {
                        Text(
                            text = "Custom",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                        )
                    }
                }

                if (customMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { input ->
                            val digits = input.filter { it.isDigit() }.take(4)
                            customText = digits
                            val parsed = digits.toIntOrNull()
                            if (parsed != null && parsed in 1..9999) {
                                onSpeedChange(displayToKmh(parsed.toFloat(), unitSystem))
                            }
                        },
                        label = { Text("Custom speed (${speedUnitLabel(unitSystem)})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { state ->
                                if (!state.isFocused) {
                                    val v = customText.toIntOrNull()?.coerceIn(1, 9999) ?: customDefaultDisplay
                                    customText = v.toString()
                                    onSpeedChange(displayToKmh(v.toFloat(), unitSystem))
                                }
                            },
                    )
                }
            }
        }
    }
}

private data class IntervalOption(val label: String, val ms: Long)

private val intervalOptions = listOf(
    IntervalOption("4HZ", 250L),
    IntervalOption("2HZ", 500L),
    IntervalOption("1HZ", 1000L),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateRateCard(
    intervalMs: Long,
    onIntervalChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
) {
    val cardPadding = if (isCompact) 8.dp else 16.dp
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(cardPadding)) {
            Text(
                text = "GPS Update Rate",
                style = if (isCompact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (!isCompact) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Most devices run at 1HZ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 8.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                intervalOptions.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = intervalMs == option.ms,
                        onClick = { onIntervalChange(option.ms) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = intervalOptions.size,
                        ),
                    ) {
                        Text(
                            option.label,
                            style = if (isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(
    progress: Float,
    currentPoint: TrackPoint?,
    distanceTraveledMeters: Double,
    unitSystem: UnitSystem = UnitSystem.Metric,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Mocking Active",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "%.1f%% complete".format(progress * 100),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "${formatDistanceKm(distanceTraveledMeters / 1000.0, unitSystem)} traveled",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            currentPoint?.let { p ->
                Text(
                    text = "Lat: %.6f  Lon: %.6f".format(p.latitude, p.longitude),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun FixedLocationCard(
    enabled: Boolean,
    lat: String,
    lon: String,
    onEnabledChange: (Boolean) -> Unit,
    onLatChange: (String) -> Unit,
    onLonChange: (String) -> Unit,
    isRunning: Boolean,
) {
    val latError = lat.isNotEmpty() && parseLatitude(lat) == null
    val lonError = lon.isNotEmpty() && parseLongitude(lon) == null

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Fixed Location", style = MaterialTheme.typography.titleSmall)
                Switch(checked = enabled, onCheckedChange = onEnabledChange, enabled = !isRunning)
            }
            if (enabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = lat,
                        onValueChange = onLatChange,
                        label = { Text("Latitude") },
                        placeholder = { Text("49.3105") },
                        isError = latError,
                        supportingText = if (latError) {
                            { Text("−90 to 90") }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        enabled = !isRunning,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = lon,
                        onValueChange = onLonChange,
                        label = { Text("Longitude") },
                        placeholder = { Text("8.5592") },
                        isError = lonError,
                        supportingText = if (lonError) {
                            { Text("−180 to 180") }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        enabled = !isRunning,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MockControlButton(
    isRunning: Boolean,
    canStart: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Button(
        onClick = if (isRunning) onStop else onStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = isRunning || canStart,
        colors = if (isRunning) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Icon(
            imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = null,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isRunning) "Stop Mocking" else "Start Mocking",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun MockLocationPermissionDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mock Location Not Enabled") },
        text = {
            Text(
                "This app needs to be set as the mock location app in Developer Options.\n\n" +
                        "Go to Settings → Developer Options → Select mock location app → " +
                        "choose LocoMocktion."
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text("Open Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun TrackSelectionDialog(
    tracks: List<GpxTrack>,
    unitSystem: UnitSystem = UnitSystem.Metric,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Track") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "This file contains ${tracks.size} tracks. Choose one to load:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                tracks.forEachIndexed { index, track ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) },
                        shape = RoundedCornerShape(8.dp),
                        tonalElevation = 2.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Route,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.name ?: "Track ${index + 1}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "${track.points.size} points · ${formatDistanceKm(track.totalDistanceKm, unitSystem, decimals = 1)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (index < tracks.lastIndex) Spacer(modifier = Modifier.height(6.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun SettingsDialog(
    unitSystem: UnitSystem,
    onUnitSystemChange: (UnitSystem) -> Unit,
    onCheckUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Units",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = unitSystem == UnitSystem.Metric,
                            onClick = { onUnitSystemChange(UnitSystem.Metric) },
                            label = { Text("Metric (km, km/h)") },
                        )
                        FilterChip(
                            selected = unitSystem == UnitSystem.Imperial,
                            onClick = { onUnitSystemChange(UnitSystem.Imperial) },
                            label = { Text("Imperial (mi, mph)") },
                        )
                    }
                }

                HorizontalDivider()

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Updates",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Button(
                        onClick = {
                            onCheckUpdate()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Check for update")
                    }
                    Text(
                        text = "Installed version: ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun UpdateDialog(
    state: UpdateState,
    onDownload: (ReleaseInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    LaunchedEffect(state) {
        if (state is UpdateState.ReadyToInstall) {
            launchInstaller(context, state.file)
            onDismiss()
        }
    }

    when (state) {
        is UpdateState.Idle, is UpdateState.ReadyToInstall -> return
        is UpdateState.Checking -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Checking for update") },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Contacting GitHub…")
                    }
                },
                confirmButton = {},
            )
        }
        is UpdateState.UpToDate -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("You're up to date") },
                text = { Text("Installed version v${state.current} is the latest release.") },
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text("OK") }
                },
            )
        }
        is UpdateState.Available -> {
            val info = state.info
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Column {
                        Text("Update available")
                        Text(
                            text = info.name.ifEmpty { info.tagName },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = "Installed: v${BuildConfig.VERSION_NAME}  ·  Latest: ${info.tagName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = info.body.ifBlank { "No release notes." },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { onDownload(info) },
                        enabled = !info.apkDownloadUrl.isNullOrEmpty(),
                    ) {
                        Text("Download & Install")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                },
            )
        }
        is UpdateState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Downloading update") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "${(state.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                confirmButton = {},
            )
        }
        is UpdateState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Update check failed") },
                text = {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text("OK") }
                },
            )
        }
    }
}
