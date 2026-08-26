package ir.keyvanadili.noghteyab.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ir.keyvanadili.noghteyab.R
import ir.keyvanadili.noghteyab.data.AppDatabase
import ir.keyvanadili.noghteyab.data.CategoryEntity
import ir.keyvanadili.noghteyab.data.GeoPoint
import ir.keyvanadili.noghteyab.data.TrackEntity
import ir.keyvanadili.noghteyab.data.TrackPointEntity
import ir.keyvanadili.noghteyab.service.LocationRepository
import ir.keyvanadili.noghteyab.service.LocationTrackingService
import ir.keyvanadili.noghteyab.ui.theme.AppButtonShape
import ir.keyvanadili.noghteyab.ui.theme.NoghteYabTheme
import ir.keyvanadili.noghteyab.util.BackupManager
import ir.keyvanadili.noghteyab.util.LocationUtil
import ir.keyvanadili.noghteyab.util.MapsUtil
import ir.keyvanadili.noghteyab.util.TrackBackup
import ir.keyvanadili.noghteyab.util.TrackPointBackup
import ir.keyvanadili.noghteyab.util.TrackUtil
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PendingAction { TRACK_LOCATION, QUICK_ADD, RECORD }

class MainActivity : ComponentActivity() {

    private var pendingAction: PendingAction? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            when (pendingAction) {
                PendingAction.TRACK_LOCATION -> startLocationService()
                PendingAction.QUICK_ADD -> addPointQuick()
                PendingAction.RECORD -> startRecordingTrack()
                null -> {}
            }
        }
        pendingAction = null
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            lifecycleScope.launch {
                val db = AppDatabase.getInstance(this@MainActivity)
                val points = db.geoPointDao().getAllOnce()
                val categories = db.categoryDao().getAllOnce().map { it.name }
                val tracks = db.trackDao().getAllTracksOnce().map { t ->
                    val trackPoints = db.trackDao().getPointsOnce(t.id).map { p ->
                        TrackPointBackup(
                            latitude = p.latitude,
                            longitude = p.longitude,
                            timestamp = p.timestamp
                        )
                    }
                    TrackBackup(
                        name = t.name,
                        startTime = t.startTime,
                        endTime = t.endTime,
                        distanceMeters = t.distanceMeters,
                        points = trackPoints
                    )
                }
                val json = BackupManager.toJson(points, categories, tracks)
                BackupManager.writeToUri(this@MainActivity, uri, json)
            }
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            lifecycleScope.launch {
                val json = BackupManager.readFromUri(this@MainActivity, uri)
                val backup = BackupManager.fromJson(json)
                val db = AppDatabase.getInstance(this@MainActivity)
                backup.points.forEach { db.geoPointDao().insert(it) }
                backup.categories.forEach { db.categoryDao().insert(CategoryEntity(name = it)) }
                backup.tracks.forEach { trackBackup ->
                    val newTrackId = db.trackDao().insertTrack(
                        TrackEntity(
                            name = trackBackup.name,
                            startTime = trackBackup.startTime,
                            endTime = trackBackup.endTime,
                            distanceMeters = trackBackup.distanceMeters
                        )
                    )
                    trackBackup.points.forEach { p ->
                        db.trackDao().insertPoint(
                            TrackPointEntity(
                                trackId = newTrackId,
                                latitude = p.latitude,
                                longitude = p.longitude,
                                timestamp = p.timestamp
                            )
                        )
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NoghteYabTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                onOpenSettings = { navController.navigate("settings") },
                                onOpenTracks = { navController.navigate("tracks") },
                                onRequestTracking = { ensurePermissionAndTrackLocation() },
                                onStopTracking = { stopLocationService() },
                                onAddPoint = { ensurePermissionAndQuickAdd() },
                                onRequestRecording = { ensurePermissionAndRecord() },
                                onStopRecording = { stopRecordingTrack() }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                onExport = { exportLauncher.launch(BackupManager.defaultFileName()) },
                                onImport = { importLauncher.launch(arrayOf("application/json")) }
                            )
                        }
                        composable("tracks") {
                            TracksScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }

    private fun hasLocationPermission(): Boolean = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun ensurePermissionAndTrackLocation() {
        if (hasLocationPermission()) {
            startLocationService()
        } else {
            pendingAction = PendingAction.TRACK_LOCATION
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun ensurePermissionAndQuickAdd() {
        if (hasLocationPermission()) {
            addPointQuick()
        } else {
            pendingAction = PendingAction.QUICK_ADD
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun ensurePermissionAndRecord() {
        if (hasLocationPermission()) {
            startRecordingTrack()
        } else {
            pendingAction = PendingAction.RECORD
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    /** Same "capture now, name later" flow as the widget, triggered from inside the app. */
    private fun addPointQuick() {
        lifecycleScope.launch {
            val location = LocationUtil.getQuickLocation(this@MainActivity)
            if (location == null) {
                Toast.makeText(this@MainActivity, getString(R.string.location_not_found), Toast.LENGTH_LONG).show()
                return@launch
            }
            val dao = AppDatabase.getInstance(this@MainActivity).geoPointDao()
            val newId = dao.insert(
                GeoPoint(
                    name = "",
                    category = "",
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timestamp = System.currentTimeMillis()
                )
            )
            startActivity(
                Intent(this@MainActivity, NameEntryActivity::class.java)
                    .putExtra(NameEntryActivity.EXTRA_POINT_ID, newId)
            )
        }
    }

    private fun startLocationService() {
        val intent = Intent(this, LocationTrackingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopLocationService() {
        stopService(Intent(this, LocationTrackingService::class.java))
    }

    private fun startRecordingTrack() {
        // Recording needs the location service running; starting it is harmless if already up.
        startLocationService()
        lifecycleScope.launch {
            val name = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("fa")).format(Date())
            val db = AppDatabase.getInstance(this@MainActivity)
            val trackId = db.trackDao().insertTrack(
                TrackEntity(name = name, startTime = System.currentTimeMillis())
            )
            LocationRepository.startRecording(trackId)
        }
    }

    private fun stopRecordingTrack() {
        val trackId = LocationRepository.currentTrackId.value
        LocationRepository.stopRecording()
        if (trackId == null) return
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@MainActivity)
            val points = db.trackDao().getPointsOnce(trackId)
            val distance = TrackUtil.totalDistanceMeters(points)
            db.trackDao().finishTrack(trackId, System.currentTimeMillis(), distance)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenTracks: () -> Unit,
    onRequestTracking: () -> Unit,
    onStopTracking: () -> Unit,
    onAddPoint: () -> Unit,
    onRequestRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    var query by remember { mutableStateOf("") }
    var points by remember { mutableStateOf(listOf<GeoPoint>()) }
    val isTracking by LocationRepository.isTracking.collectAsStateWithLifecycle()
    val isRecording by LocationRepository.isRecording.collectAsStateWithLifecycle()
    val location by LocationRepository.currentLocation.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(query) {
        val dao = db.geoPointDao()
        val flow = if (query.isBlank()) dao.getAll() else dao.search(query)
        flow.collectLatest { points = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenTracks) {
                        Icon(Icons.Filled.Route, contentDescription = "مسیرها")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "تنظیمات")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddPoint, shape = AppButtonShape) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_point))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {

            // Current location + track recording, side by side to save vertical space
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(R.string.current_location),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = isTracking,
                                onCheckedChange = { checked ->
                                    if (checked) onRequestTracking() else onStopTracking()
                                },
                                modifier = Modifier.scale(0.8f)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        if (location != null) {
                            Text(
                                "${"%.4f".format(location!!.latitude)}, ${"%.4f".format(location!!.longitude)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                if (isTracking) "در حال دریافت..." else "غیرفعال",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Card(
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "ضبط مسیر",
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = isRecording,
                                onCheckedChange = { checked ->
                                    if (checked) onRequestRecording() else onStopRecording()
                                },
                                modifier = Modifier.scale(0.8f)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (isRecording) "در حال ضبط..." else "غیرفعال",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            if (points.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_points_yet), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(points, key = { it.id }) { point ->
                        PointRow(
                            point = point,
                            onOpenMaps = { MapsUtil.openInMaps(context, point) },
                            onEdit = {
                                context.startActivity(
                                    Intent(context, NameEntryActivity::class.java)
                                        .putExtra(NameEntryActivity.EXTRA_POINT_ID, point.id)
                                )
                            },
                            onDelete = {
                                scope.launch { db.geoPointDao().delete(point) }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PointRow(point: GeoPoint, onOpenMaps: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var showConfirmDelete by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val coordsText = "${"%.6f".format(point.latitude)}, ${"%.6f".format(point.longitude)}"

    Card(
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    clipboard.setText(AnnotatedString(coordsText))
                    Toast.makeText(context, "مختصات کپی شد", Toast.LENGTH_SHORT).show()
                }
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                point.name.ifBlank { "بدون نام" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (point.category.isNotBlank()) {
                        Text(
                            point.category,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        coordsText,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit))
                    }
                    IconButton(onClick = onOpenMaps) {
                        Icon(Icons.Filled.Map, contentDescription = stringResource(R.string.open_in_maps))
                    }
                    IconButton(onClick = { showConfirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
            }
        }
    }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text("این نقطه حذف شود؟ (${point.name.ifBlank { "بدون نام" }})") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
