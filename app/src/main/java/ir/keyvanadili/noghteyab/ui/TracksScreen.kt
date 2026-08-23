package ir.keyvanadili.noghteyab.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.keyvanadili.noghteyab.R
import ir.keyvanadili.noghteyab.data.AppDatabase
import ir.keyvanadili.noghteyab.data.TrackEntity
import ir.keyvanadili.noghteyab.ui.theme.AppButtonShape
import ir.keyvanadili.noghteyab.util.GpxKmlExporter
import ir.keyvanadili.noghteyab.util.TrackUtil
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracksScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    val tracks by db.trackDao().getAllTracks().collectAsStateWithLifecycle(initialValue = emptyList())
    var trackPendingDelete by remember { mutableStateOf<TrackEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مسیرها") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "بازگشت")
                    }
                }
            )
        }
    ) { padding ->
        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Route,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("هنوز مسیری ضبط نشده", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        onExportGpx = {
                            scope.launch {
                                val points = db.trackDao().getPointsOnce(track.id)
                                if (points.isEmpty()) return@launch
                                GpxKmlExporter.shareGpx(context, track, points)
                            }
                        },
                        onExportKml = {
                            scope.launch {
                                val points = db.trackDao().getPointsOnce(track.id)
                                if (points.isEmpty()) return@launch
                                GpxKmlExporter.shareKml(context, track, points)
                            }
                        },
                        onDelete = { trackPendingDelete = track }
                    )
                }
            }
        }
    }

    trackPendingDelete?.let { track ->
        AlertDialog(
            onDismissRequest = { trackPendingDelete = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text("این مسیر حذف شود؟ (${track.name})") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { db.trackDao().deleteTrackWithPoints(track) }
                    trackPendingDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { trackPendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun TrackRow(
    track: TrackEntity,
    onExportGpx: () -> Unit,
    onExportKml: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("fa")) }
    val statusText = if (track.endTime == null) "در حال ضبط..." else TrackUtil.formatDistance(track.distanceMeters)

    Card(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.name, style = MaterialTheme.typography.titleMedium)
                    Text(statusText, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onExportGpx, shape = AppButtonShape, modifier = Modifier.weight(1f)) {
                    Text("GPX")
                }
                OutlinedButton(onClick = onExportKml, shape = AppButtonShape, modifier = Modifier.weight(1f)) {
                    Text("KML")
                }
            }
        }
    }
}
