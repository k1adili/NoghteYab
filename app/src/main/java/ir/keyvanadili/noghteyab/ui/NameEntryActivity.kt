package ir.keyvanadili.noghteyab.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import ir.keyvanadili.noghteyab.R
import ir.keyvanadili.noghteyab.data.AppDatabase
import ir.keyvanadili.noghteyab.data.GeoPoint
import ir.keyvanadili.noghteyab.ui.theme.NoghteYabTheme
import kotlinx.coroutines.launch

private val DEFAULT_CATEGORIES = listOf("مغازه", "رستوران", "تعمیرگاه", "سوپرمارکت", "داروخانه", "پمپ بنزین", "سایر")

class NameEntryActivity : ComponentActivity() {

    private var pointId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pointId = intent.getLongExtra(EXTRA_POINT_ID, -1L)
        if (pointId == -1L) { finish(); return }

        setContent {
            NoghteYabTheme {
                NameEntryScreen(
                    onSave = { name, category -> savePoint(name, category) },
                    onDismiss = { finish() }
                )
            }
        }
    }

    private fun savePoint(name: String, category: String) {
        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@NameEntryActivity).geoPointDao()
            val existing = dao.getById(pointId)
            if (existing != null) {
                dao.update(existing.copy(name = name.ifBlank { "نقطه بدون نام" }, category = category))
            }
            finish()
        }
    }

    companion object {
        const val EXTRA_POINT_ID = "extra_point_id"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NameEntryScreen(onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) name = text
        }
    }

    fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "اسم مکان را بگویید")
        }
        speechLauncher.launch(intent)
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource_point_saved(), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم مکان") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { startVoiceInput() }) {
                            Icon(Icons.Filled.Mic, contentDescription = "ورودی صوتی")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))
                Text("دسته‌بندی", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))

                FlowRowChips(
                    options = DEFAULT_CATEGORIES,
                    selected = category,
                    onSelect = { category = it }
                )

                Spacer(Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("بعداً")
                    }
                    Button(
                        onClick = { onSave(name, category) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("ذخیره")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowChips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (opt in options) {
            FilterChip(
                selected = selected == opt,
                onClick = { onSelect(opt) },
                label = { Text(opt) }
            )
        }
    }
}

@Composable
private fun stringResource_point_saved(): String {
    return androidx.compose.ui.res.stringResource(R.string.point_saved)
}
