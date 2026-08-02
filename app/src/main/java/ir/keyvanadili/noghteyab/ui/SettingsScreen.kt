package ir.keyvanadili.noghteyab.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.keyvanadili.noghteyab.data.AppDatabase
import ir.keyvanadili.noghteyab.data.CategoryEntity
import ir.keyvanadili.noghteyab.util.DEFAULT_CATEGORIES
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        db.categoryDao().seedDefaultsIfEmpty(DEFAULT_CATEGORIES)
    }

    val categories by db.categoryDao().getAll().collectAsStateWithLifecycle(initialValue = emptyList())
    var newCategory by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تنظیمات") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "بازگشت")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Backup section
            Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("پشتیبان‌گیری", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Upload, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("گرفتن بک‌آپ")
                        }
                        OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Download, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("بازیابی")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Categories section
            Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                    Text("دسته‌بندی‌ها", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newCategory,
                            onValueChange = { newCategory = it },
                            label = { Text("دسته‌بندی جدید") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = {
                            val name = newCategory.trim()
                            if (name.isNotEmpty()) {
                                scope.launch {
                                    db.categoryDao().insert(CategoryEntity(name = name))
                                }
                                newCategory = ""
                            }
                        }) {
                            Text("افزودن")
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(categories, key = { it.id }) { category ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(category.name, style = MaterialTheme.typography.bodyLarge)
                                IconButton(onClick = {
                                    scope.launch { db.categoryDao().delete(category) }
                                }) {
                                    Icon(Icons.Filled.Close, contentDescription = "حذف دسته‌بندی")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
