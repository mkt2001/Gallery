package com.mahesh.gallerypro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mahesh.gallerypro.data.GalleryViewModel
import com.mahesh.gallerypro.data.Media
import com.mahesh.gallerypro.data.MediaFilter
import com.mahesh.gallerypro.data.SortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: GalleryViewModel,
    onOpen: (List<Media>, Media) -> Unit,
    onShare: (List<Media>) -> Unit,
    onDelete: (List<Media>) -> Unit,
    onHide: (List<Media>) -> Unit,
    onOpenVault: () -> Unit,
    onOpenTrash: () -> Unit,
    onGrant: () -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openAlbum by rememberSaveable { mutableStateOf<Long?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val selecting = vm.selection.isNotEmpty()

    BackHandler(selecting) { vm.clearSelection() }
    BackHandler(!selecting && openAlbum != null) { openAlbum = null }
    BackHandler(!selecting && searching) { searching = false; vm.updateQuery("") }

    val album = openAlbum?.let { id -> vm.albums.firstOrNull { it.bucketId == id } }
    val albumItems = remember(vm.media, vm.sort, vm.query, openAlbum) {
        vm.arrange(vm.media.filter { it.bucketId == openAlbum })
    }
    val favItems = remember(vm.favoriteItems, vm.sort, vm.query) { vm.arrange(vm.favoriteItems) }
    val current: List<Media> = when (tab) {
        0 -> vm.shown
        1 -> if (openAlbum != null) albumItems else emptyList()
        else -> favItems
    }
    val selectedItems = vm.media.filter { it.key in vm.selection }
    val titles = listOf("Photos", "Albums", "Favorites")

    Scaffold(
        topBar = {
            when {
                selecting -> TopAppBar(
                    title = { Text("${vm.selection.size} selected") },
                    navigationIcon = {
                        IconButton({ vm.clearSelection() }) { Icon(Icons.Filled.Close, "Cancel") }
                    },
                    actions = {
                        IconButton({ vm.selectAll(current.map { it.key }) }) { Icon(Icons.Filled.SelectAll, "Select all") }
                        IconButton({ vm.favoriteSelected() }) { Icon(Icons.Filled.Favorite, "Favorite") }
                        IconButton({ onShare(selectedItems) }) { Icon(Icons.Filled.Share, "Share") }
                        IconButton({ onHide(selectedItems) }) { Icon(Icons.Filled.Lock, "Hide in vault") }
                        IconButton({ onDelete(selectedItems) }) { Icon(Icons.Filled.Delete, "Delete") }
                    }
                )
                searching -> {
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focus.requestFocus() }
                    TopAppBar(
                        title = {
                            TextField(
                                value = vm.query,
                                onValueChange = vm::updateQuery,
                                placeholder = { Text("Search by name or album") },
                                singleLine = true,
                                modifier = Modifier.focusRequester(focus),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                        },
                        navigationIcon = {
                            IconButton({ searching = false; vm.updateQuery("") }) {
                                Icon(Icons.Filled.ArrowBack, "Close search")
                            }
                        }
                    )
                }
                else -> TopAppBar(
                    title = { Text(if (tab == 1 && album != null) album.name else titles[tab]) },
                    navigationIcon = {
                        if (tab == 1 && openAlbum != null) {
                            IconButton({ openAlbum = null }) { Icon(Icons.Filled.ArrowBack, "Back") }
                        }
                    },
                    actions = {
                        IconButton({ searching = true }) { Icon(Icons.Filled.Search, "Search") }
                        Box {
                            IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, "Menu") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Private vault") },
                                    onClick = { menu = false; onOpenVault() },
                                    trailingIcon = { Icon(Icons.Filled.Lock, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Trash") },
                                    onClick = { menu = false; onOpenTrash() },
                                    trailingIcon = { Icon(Icons.Filled.Delete, null) }
                                )
                                MenuLabel("Sort by")
                                SortOrder.values().forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s.label) },
                                        onClick = { vm.updateSort(s); menu = false },
                                        trailingIcon = { if (vm.sort == s) Icon(Icons.Filled.Check, null) }
                                    )
                                }
                                MenuLabel("Grid columns")
                                (2..5).forEach { c ->
                                    DropdownMenuItem(
                                        text = { Text("$c columns") },
                                        onClick = { vm.updateColumns(c); menu = false },
                                        trailingIcon = { if (vm.columns == c) Icon(Icons.Filled.Check, null) }
                                    )
                                }
                                MenuLabel("Theme")
                                listOf("SYSTEM" to "System default", "LIGHT" to "Light", "DARK" to "Dark").forEach { (k, v) ->
                                    DropdownMenuItem(
                                        text = { Text(v) },
                                        onClick = { vm.updateTheme(k); menu = false },
                                        trailingIcon = { if (vm.theme == k) Icon(Icons.Filled.Check, null) }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (!selecting) {
                NavigationBar {
                    val icons = listOf(Icons.Filled.Photo, Icons.Filled.PhotoAlbum, Icons.Filled.Favorite)
                    titles.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = {
                                if (i != 1 || tab == 1) openAlbum = null
                                tab = i
                            },
                            icon = { Icon(icons[i], t) },
                            label = { Text(t) }
                        )
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                !vm.hasPermission -> PermissionPrompt(onGrant)
                vm.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                tab == 1 && openAlbum == null ->
                    if (vm.albums.isEmpty()) EmptyState("No albums found")
                    else AlbumsGrid(vm.albums) { openAlbum = it.bucketId }
                else -> Column(Modifier.fillMaxSize()) {
                    if (tab == 0) {
                        Row(
                            Modifier.padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MediaFilter.values().forEach { f ->
                                FilterChip(
                                    selected = vm.filter == f,
                                    onClick = { vm.updateFilter(f) },
                                    label = { Text(f.label) }
                                )
                            }
                        }
                    }
                    if (current.isEmpty()) {
                        EmptyState(if (tab == 2) "No favorites yet" else "Nothing to show")
                    } else {
                        MediaGrid(
                            items = current,
                            columns = vm.columns,
                            grouped = vm.sort == SortOrder.NEWEST || vm.sort == SortOrder.OLDEST,
                            selection = vm.selection,
                            favorites = vm.favorites,
                            onClick = { m ->
                                if (selecting) vm.toggleSelect(m.key) else onOpen(current, m)
                            },
                            onLongClick = { m -> vm.toggleSelect(m.key) },
                            onHeaderClick = { keys -> vm.selectAll(keys) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Gallery Pro needs access to your photos and videos.", textAlign = TextAlign.Center)
        Button(onClick = onGrant, modifier = Modifier.padding(top = 16.dp)) { Text("Allow access") }
    }
}
