package com.locomocktion.ui.screens

import android.content.Intent
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.locomocktion.MainViewModel
import com.locomocktion.gpx.GpxTrack
import com.locomocktion.gpx.TrackPoint
import com.locomocktion.gpx.distanceBetween
import com.locomocktion.service.TravelMode
import com.locomocktion.util.isMockLocationEnabled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val currentPoint by viewModel.currentPoint.collectAsState()
    val distanceTraveled by viewModel.distanceTraveled.collectAsState()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        val name = cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) it.getString(idx) else null
            } else null
        }
        viewModel.loadGpx(uri, name)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var showMockLocationDialog by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LocoMocktion") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // File selection card
            FileSelectionCard(
                fileName = uiState.fileName,
                track = uiState.track,
                isRunning = isRunning,
                onPickFile = { filePicker.launch(arrayOf("application/*", "*/*")) },
            )

            // Track preview
            uiState.track?.let { track ->
                TrackPreviewCard(
                    track = track,
                    startKm = uiState.startKm,
                    endKm = uiState.endKm,
                    currentPoint = currentPoint,
                    onStartChange = viewModel::setStartKm,
                    onEndChange = viewModel::setEndKm,
                    isRunning = isRunning,
                )

                // Track range (start / end)
                TrackRangeCard(
                    startKm = uiState.startKm,
                    endKm = uiState.endKm,
                    totalDistanceKm = track.totalDistanceMeters / 1000.0,
                    onStartChange = viewModel::setStartKm,
                    onEndChange = viewModel::setEndKm,
                    isRunning = isRunning,
                )

                // Travel mode
                TravelModeCard(
                    travelMode = uiState.travelMode,
                    onModeChange = viewModel::setTravelMode,
                    isRunning = isRunning,
                )
            }

            // Speed control
            SpeedControlCard(
                speedKmh = uiState.speedKmh,
                onSpeedChange = viewModel::setSpeed,
            )

            // Update rate control
            UpdateRateCard(
                intervalMs = uiState.updateIntervalMs,
                onIntervalChange = viewModel::setUpdateInterval,
            )

            // Progress
            if (isRunning) {
                ProgressCard(
                    progress = progress,
                    currentPoint = currentPoint,
                    distanceTraveledMeters = distanceTraveled,
                )
            }

            // Start/Stop button
            MockControlButton(
                isRunning = isRunning,
                hasTrack = uiState.track != null,
                onStart = {
                    if (isMockLocationEnabled(context)) {
                        viewModel.startMocking()
                    } else {
                        showMockLocationDialog = true
                    }
                },
                onStop = viewModel::stopMocking,
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
) {
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
                                    "%.2f km".format(track.totalDistanceMeters / 1000),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedButton(
                onClick = onPickFile,
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.FileOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (fileName == null) "Select GPX file" else "Change file")
            }
        }
    }
}

@Composable
private fun TrackPreviewCard(
    track: GpxTrack,
    startKm: Float,
    endKm: Float,
    currentPoint: TrackPoint?,
    onStartChange: (Float) -> Unit,
    onEndChange: (Float) -> Unit,
    isRunning: Boolean,
) {
    val points = track.points

    // Pre-compute cumulative distances along the track
    val cumulativeDistances = remember(track) {
        val dists = mutableListOf(0.0)
        for (i in 0 until points.size - 1) {
            dists.add(dists.last() + distanceBetween(points[i], points[i + 1]))
        }
        dists
    }
    val totalDistMeters = cumulativeDistances.last()

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
                return TrackPoint(
                    points[i].latitude + (points[i + 1].latitude - points[i].latitude) * f,
                    points[i].longitude + (points[i + 1].longitude - points[i].longitude) * f,
                )
            }
        }
        return points.last()
    }

    // Use rememberUpdatedState so the pointer input closure always sees latest values
    val currentStartKm by rememberUpdatedState(startKm)
    val currentEndKm by rememberUpdatedState(endKm)
    val currentIsRunning by rememberUpdatedState(isRunning)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = track.name ?: "Track Preview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            val trackColor = MaterialTheme.colorScheme.primary

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .pointerInput(track) {
                        val paddingPx = 16.dp.toPx()
                        val w = size.width - 2 * paddingPx
                        val h = size.height - 2 * paddingPx
                        val hitRadiusPx = 24.dp.toPx()

                        fun toScreen(p: TrackPoint): Offset {
                            val x = ((p.longitude - minLon) / lonRange * w + paddingPx).toFloat()
                            val y = ((1 - (p.latitude - minLat) / latRange) * h + paddingPx).toFloat()
                            return Offset(x, y)
                        }

                        val screenPositions = points.map { toScreen(it) }

                        fun nearestMetersOnTrack(touchPos: Offset): Double {
                            var bestDistSq = Float.MAX_VALUE
                            var bestMeters = 0.0
                            for (i in 0 until screenPositions.size - 1) {
                                val a = screenPositions[i]
                                val b = screenPositions[i + 1]
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

                            val diffStart = down.position - startPos
                            val dStart = kotlin.math.sqrt(diffStart.x * diffStart.x + diffStart.y * diffStart.y)
                            val diffEnd = down.position - endPos
                            val dEnd = kotlin.math.sqrt(diffEnd.x * diffEnd.x + diffEnd.y * diffEnd.y)

                            val target = when {
                                dStart <= hitRadiusPx && dStart <= dEnd -> "start"
                                dEnd <= hitRadiusPx -> "end"
                                else -> null
                            }

                            if (target == null) return@awaitEachGesture

                            // Consume the down event to prevent scroll from intercepting
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
                        }
                    },
            ) {
                if (points.size < 2) return@Canvas

                val paddingPx = 16.dp.toPx()
                val w = size.width - 2 * paddingPx
                val h = size.height - 2 * paddingPx

                fun toOffset(p: TrackPoint): Offset {
                    val x = ((p.longitude - minLon) / lonRange * w + paddingPx).toFloat()
                    val y = ((1 - (p.latitude - minLat) / latRange) * h + paddingPx).toFloat()
                    return Offset(x, y)
                }

                // Draw track line
                for (i in 0 until points.size - 1) {
                    drawLine(
                        color = trackColor,
                        start = toOffset(points[i]),
                        end = toOffset(points[i + 1]),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }

                // Green dot: start of travel range (draggable)
                val startPos = toOffset(positionAtMeters((startKm * 1000).toDouble()))
                drawCircle(color = Color(0xFF4CAF50), radius = 8.dp.toPx(), center = startPos)
                drawCircle(color = Color.White, radius = 4.dp.toPx(), center = startPos)

                // Blue dot: end of travel range (draggable)
                val endPos = toOffset(positionAtMeters((endKm * 1000).toDouble()))
                drawCircle(color = Color(0xFF2196F3), radius = 8.dp.toPx(), center = endPos)
                drawCircle(color = Color.White, radius = 4.dp.toPx(), center = endPos)

                // Red dot: current position (while mocking)
                currentPoint?.let { cp ->
                    drawCircle(color = Color(0xFFF44336), radius = 8.dp.toPx(), center = toOffset(cp))
                    drawCircle(color = Color.White, radius = 4.dp.toPx(), center = toOffset(cp))
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
) {
    var startText by remember(startKm) {
        mutableStateOf(if (startKm == 0f) "" else "%.2f".format(startKm))
    }
    var endText by remember(endKm) {
        mutableStateOf("%.2f".format(endKm))
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Track Range",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Track total: %.2f km".format(totalDistanceKm),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = startText,
                    onValueChange = { input ->
                        startText = input
                        val parsed = input.toFloatOrNull()
                        if (parsed != null) onStartChange(parsed)
                        else if (input.isEmpty()) onStartChange(0f)
                    },
                    label = { Text("Start (km)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { input ->
                        endText = input
                        val parsed = input.toFloatOrNull()
                        if (parsed != null) onEndChange(parsed)
                        else if (input.isEmpty()) onEndChange(totalDistanceKm.toFloat())
                    },
                    label = { Text("End (km)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f),
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
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Travel Mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))

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
                        Text(mode.label)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = travelMode.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SpeedControlCard(
    speedKmh: Float,
    onSpeedChange: (Float) -> Unit,
) {
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
                    text = "%.0f km/h".format(speedKmh),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Slider(
                value = speedKmh,
                onValueChange = onSpeedChange,
                valueRange = 1f..200f,
                steps = 0,
            )

            // Preset speed buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SpeedPreset("Walk\n5", 5f, onSpeedChange, Modifier.weight(1f))
                SpeedPreset("Bike\n20", 20f, onSpeedChange, Modifier.weight(1f))
                SpeedPreset("Car\n50", 50f, onSpeedChange, Modifier.weight(1f))
                SpeedPreset("Fast\n120", 120f, onSpeedChange, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SpeedPreset(
    label: String,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = { onSpeedChange(speed) },
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
        )
    }
}

private data class IntervalOption(val label: String, val ms: Long)

private val intervalOptions = listOf(
    IntervalOption("250ms", 250L),
    IntervalOption("500ms", 500L),
    IntervalOption("750ms", 750L),
    IntervalOption("1s", 1000L),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateRateCard(
    intervalMs: Long,
    onIntervalChange: (Long) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Update Rate",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Faster rates give smoother movement",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

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
                        Text(option.label)
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
                    text = "%.2f km traveled".format(distanceTraveledMeters / 1000),
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
private fun MockControlButton(
    isRunning: Boolean,
    hasTrack: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Button(
        onClick = if (isRunning) onStop else onStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = hasTrack,
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
