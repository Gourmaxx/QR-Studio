package com.qrstudio.ui.history

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qrstudio.core.data.HistoryItem
import com.qrstudio.core.qr.QrParser
import com.qrstudio.core.util.Formatting
import com.qrstudio.core.util.readBytesBounded
import com.qrstudio.ui.EditRequest
import com.qrstudio.ui.components.QrResultSheet
import com.qrstudio.ui.components.iconFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A 500-entry export stays well under this; anything bigger is not one of ours. */
private const val MAX_IMPORT_BYTES = 10L * 1024 * 1024

@Composable
fun HistoryScreen(
    onEditRequest: (EditRequest) -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<HistoryItem?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val json = viewModel.exportJson()
                val success = withContext(Dispatchers.IO) {
                    runCatching {
                        // "wt" truncates an existing document instead of leaving stale
                        // bytes; some providers (Drive…) only accept plain "w".
                        val stream = runCatching {
                            context.contentResolver.openOutputStream(uri, "wt")
                        }.getOrNull() ?: context.contentResolver.openOutputStream(uri)
                        stream?.use { it.write(json.toByteArray()) } != null
                    }.getOrDefault(false)
                }
                toast(if (success) "Historique exporté." else "Échec de l'export.")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val added = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            stream.readBytesBounded(MAX_IMPORT_BYTES)?.toString(Charsets.UTF_8)
                        }
                    }.getOrNull()?.let { viewModel.importJson(it) }
                }
                toast(
                    when {
                        added == null -> "Fichier invalide : ce n'est pas un export QR Studio."
                        added == 0 -> "Aucune nouvelle entrée à importer."
                        added == 1 -> "1 entrée importée."
                        else -> "$added entrées importées."
                    }
                )
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Historique",
                style = MaterialTheme.typography.headlineMedium
            )
            Row {
                IconButton(onClick = { importLauncher.launch("*/*") }) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = "Importer un historique")
                }
                if (state.hasAnyEntry) {
                    IconButton(onClick = { exportLauncher.launch("qrstudio_historique.json") }) {
                        Icon(Icons.Outlined.FileUpload, contentDescription = "Exporter l'historique")
                    }
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "Tout effacer")
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { viewModel.setFilter(filter) },
                    label = { Text(filter.label) }
                )
            }
        }

        if (state.hasAnyEntry) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Rechercher…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Effacer la recherche")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        if (state.items.isEmpty()) {
            EmptyState(
                isFilteredOut = state.hasAnyEntry,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)
            ) {
                items(state.items, key = { it.id }) { item ->
                    HistoryRow(
                        item = item,
                        onClick = { selected = item },
                        onTogglePin = { viewModel.togglePinned(item) },
                        onDelete = { viewModel.delete(item.id) }
                    )
                }
            }
        }
    }

    selected?.let { item ->
        val prefill = remember(item) { QrParser.toForm(item.content, item.type) }
        QrResultSheet(
            content = item.content,
            type = item.type,
            subtitle = "${item.origin.frenchLabel} • ${Formatting.timestamp(item.timestamp)}",
            foregroundArgb = item.foregroundArgb,
            onEdit = if (prefill != null) {
                {
                    selected = null
                    onEditRequest(EditRequest(item.content, item.type, item.foregroundArgb))
                }
            } else {
                null
            },
            onDismiss = { selected = null }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Tout effacer ?") },
            text = { Text("Tout l'historique sera supprimé définitivement.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    showClearDialog = false
                }) { Text("Effacer") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Annuler") }
            }
        )
    }
}

@Composable
private fun HistoryRow(
    item: HistoryItem,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = iconFor(item.type),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(8.dp).size(22.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            ) {
                Text(
                    text = item.label?.takeIf { it.isNotBlank() } ?: item.content,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.type.frenchLabel} • ${item.origin.frenchLabel} • " +
                        Formatting.timestamp(item.timestamp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onTogglePin) {
                Icon(
                    imageVector = if (item.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (item.pinned) "Désépingler" else "Épingler",
                    tint = if (item.pinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Close, contentDescription = "Supprimer")
            }
        }
    }
}

@Composable
private fun EmptyState(isFilteredOut: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                if (isFilteredOut) Icons.Outlined.Search else Icons.Outlined.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = if (isFilteredOut) "Aucun résultat" else "Aucun élément pour le moment",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = if (isFilteredOut) {
                    "Aucune entrée ne correspond à cette recherche ou à ce filtre."
                } else {
                    "Les QR codes que vous créez ou scannez apparaîtront ici."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
