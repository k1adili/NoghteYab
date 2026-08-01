package ir.keyvanadili.noghteyab.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import ir.keyvanadili.noghteyab.R
import ir.keyvanadili.noghteyab.data.AppDatabase
import ir.keyvanadili.noghteyab.data.GeoPoint
import ir.keyvanadili.noghteyab.service.LocationRepository
import ir.keyvanadili.noghteyab.service.LocationTrackingService
import ir.keyvanadili.noghteyab.ui.theme.NoghteYabTheme
import ir.keyvanadili.noghteyab.util.BackupManager
import ir.keyvanadili.noghteyab.util.MapsUtil
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startLocationService()
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            lifecycleScope.launch {
                val dao = AppDatabase.getInstance(this@MainActivity).geoPointDao()
                val points = dao.getAllOnce()
                val json = BackupManager.toJson(points)
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
                val points = BackupManager.fromJson(json)
                val dao = AppDatabase.getInstance(this@MainActivity).geoPointDao()
                points.forEach { dao.insert(it) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NoghteYabTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(
                        onRequestTracking = { ensurePermissionAndTrack() },
                        onStopTracking = { stopLocationService() },
                        onExport = { exportLauncher.launch(BackupManager.defaultFileName()) },
                        onImport = { importLauncher.launch(arrayOf("application/json")) }
                    )
                }
            }
        }
    }

    private fun ensurePermissionAndTrack() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            startLocationService()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onRequestTracking: () -> Unit,
    onStopTracking: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    var query by remember { mutableStateOf("") }
    var points by remember { mutableStateOf(listOf<GeoPoint>()) }
    val isTracking by LocationRepository.isTracking.collectAsStateWithLifecycle()
    val location by LocationRepository.currentLocation.collectAsStateWithLifecycle()

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
                    IconButton(onClick = onExport) {
                        Icon(Icons.Filled.Upload, contentDescription = stringResource(R.string.backup_export))
                    }
                    IconButton(onClick = onImport) {
                        Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.backup_import))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {

            // Current location card
            Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.current_location), style = MaterialTheme.typography.titleMedium)
                        Switch(
                            checked = isTracking,
                            onCheckedChange = { checked ->
                                if (checked) onRequestTracking() else onStopTracking()
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (location != null) {
                        Text("Lat: ${"%.6f".format(location!!.latitude)}")
                        Text("Lng: ${"%.6f".format(location!!.longitude)}")
                    } else {
                        Text(if (isTracking) "در حال دریافت..." else "ردیابی غیرفعال است")
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
                        PointRow(point = point, onOpenMaps = { MapsUtil.openInMaps(context, point) })
                    }
                }
            }
        }
    }
}

@Composable
fun PointRow(point: GeoPoint, onOpenMaps: () -> Unit) {
    val dateFmt = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("fa")) }
    Card(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    point.name.ifBlank { "بدون نام" },
                    style = MaterialTheme.typography.titleMedium
                )
                if (point.category.isNotBlank()) {
                    Text(point.category, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    dateFmt.format(Date(point.timestamp)),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            IconButton(onClick = onOpenMaps) {
                Icon(Icons.Filled.Map, contentDescription = stringResource(R.string.open_in_maps))
            }
        }
    }
}
