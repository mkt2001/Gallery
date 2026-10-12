package com.mahesh.gallerypro.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mahesh.gallerypro.data.GalleryViewModel
import com.mahesh.gallerypro.data.Media

/** Items deleted from the gallery stay in the system Trash (Android 11+) for ~30 days. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    vm: GalleryViewModel,
    onRestore: (List<Media>) -> Unit,
    onDeleteForever: (List<Media>) -> Unit,
    onClose: () -> Unit
) {
    var selection by remember { mutableStateOf<Set<Long>>(emptySet()) }
    LaunchedEffect(Unit) { vm.loadTrash() }
    val items = vm.trash
    BackHandler { if (selection.isNotEmpty()) selection = emptySet() else onClose() }

    Scaffold(
        topBar = {
            if (selection.isNotEmpty()) {
                val sel = items.filter { it.key in selection }
                TopAppBar(
                    title = { Text("${sel.size} selected") },
                    navigationIcon = { IconButton({ selection = emptySet() }) { Icon(Icons.Filled.Close, "Cancel") } },
                    actions = {
                        IconButton({ selection = items.map { it.key }.toSet() }) { Icon(Icons.Filled.SelectAll, "Select all") }
                        IconButton({ onRestore(sel); selection = emptySet() }) { Icon(Icons.Filled.Restore, "Restore") }
                        IconButton({ onDeleteForever(sel); selection = emptySet() }) { Icon(Icons.Filled.DeleteForever, "Delete forever") }
                    }
                )
            } else TopAppBar(
                title = { Text("Trash") },
                navigationIcon = { IconButton(onClose) { Icon(Icons.Filled.ArrowBack, "Back") } },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton({ onDeleteForever(items) }) { Icon(Icons.Filled.DeleteForever, "Empty trash") }
                    }
                }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                Build.VERSION.SDK_INT < 30 -> Text(
                    "Trash needs Android 11 or newer. On this phone, deleted items are removed permanently.",
                    Modifier.align(Alignment.Center).padding(32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                items.isEmpty() -> Text(
                    "Trash is empty.\nDeleted photos stay here for about 30 days.",
                    Modifier.align(Alignment.Center).padding(32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> MediaGrid(
                    items = items, columns = vm.columns, grouped = false,
                    selection = selection, favorites = emptySet(),
                    onClick = { m -> if (selection.isNotEmpty()) selection = if (m.key in selection) selection - m.key else selection + m.key },
                    onLongClick = { m -> selection = if (m.key in selection) selection - m.key else selection + m.key },
                    onHeaderClick = {}
                )
            }
        }
    }
}
