package com.mahesh.gallerypro.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import com.mahesh.gallerypro.data.VaultItem
import com.mahesh.gallerypro.data.VaultStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/** Entry point of the private vault: lock screen -> vault content. */
@Composable
fun VaultScreen(store: VaultStore, onUnlockedChange: (Boolean) -> Unit, onClose: () -> Unit) {
    var unlocked by remember { mutableStateOf(false) }
    LaunchedEffect(unlocked) { onUnlockedChange(unlocked) }
    DisposableEffect(Unit) { onDispose { onUnlockedChange(false); store.clearCache() } }
    if (unlocked) VaultContent(store, onClose)
    else {
        BackHandler(onBack = onClose)
        LockScreen(store, onUnlocked = { unlocked = true }, onClose = onClose)
    }
}

// ---------------- lock screen ----------------

@Composable
private fun LockScreen(store: VaultStore, onUnlocked: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val setup = !store.isPinSet
    var first by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf(0) } // setup: 0 enter, 1 confirm
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var wait by remember { mutableStateOf(store.lockedSeconds()) }

    val canBio = !setup && store.biometricEnabled &&
        BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
        BiometricManager.BIOMETRIC_SUCCESS

    fun promptBio() {
        val act = ctx as? FragmentActivity ?: return
        val prompt = BiometricPrompt(act, ContextCompat.getMainExecutor(act),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onUnlocked()
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock private vault")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info)
    }

    LaunchedEffect(Unit) { if (canBio && wait == 0L) promptBio() }
    LaunchedEffect(wait) { if (wait > 0) { delay(1000); wait = store.lockedSeconds() } }

    fun submit() {
        if (setup) {
            if (stage == 0) { first = pin; pin = ""; stage = 1; message = "" }
            else if (pin == first) { store.setPin(pin); onUnlocked() }
            else { message = "PINs do not match. Try again."; pin = ""; stage = 0; first = "" }
        } else {
            if (store.checkPin(pin)) onUnlocked()
            else {
                pin = ""
                wait = store.lockedSeconds()
                message = if (wait > 0) "Too many attempts. Wait ${wait}s." else "Wrong PIN"
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Row(Modifier.fillMaxWidth()) { IconButton(onClose) { Icon(Icons.Filled.Close, "Close") } }
        Spacer(Modifier.weight(1f))
        Icon(Icons.Filled.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            when {
                setup && stage == 0 -> "Create a vault PIN (4-6 digits)"
                setup -> "Confirm your PIN"
                else -> "Enter PIN"
            },
            Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium
        )
        Row(Modifier.padding(vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(6) { i ->
                Box(
                    Modifier.size(14.dp).clip(CircleShape).background(
                        if (i < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }
        Text(if (wait > 0) "Locked. Wait ${wait}s." else message, color = MaterialTheme.colorScheme.error, modifier = Modifier.height(24.dp))
        Spacer(Modifier.height(8.dp))

        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "del")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier.size(72.dp).clip(CircleShape)
                            .clickable(enabled = wait == 0L && k != "bio" || k == "bio" && canBio) {
                                when (k) {
                                    "del" -> pin = pin.dropLast(1)
                                    "bio" -> promptBio()
                                    else -> if (pin.length < 6) pin += k
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        when (k) {
                            "del" -> Icon(Icons.Filled.Backspace, "Delete")
                            "bio" -> if (canBio) Icon(Icons.Filled.Fingerprint, "Fingerprint", Modifier.size(32.dp))
                            else -> Text(k, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
        TextButton(onClick = { submit() }, enabled = pin.length >= 4 && wait == 0L) {
            Text(if (setup && stage == 0) "Next" else if (setup) "Confirm" else "Unlock")
        }
        Spacer(Modifier.weight(1f))
    }
}

// ---------------- vault content ----------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun VaultContent(store: VaultStore, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<VaultItem>>(emptyList()) }
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var viewing by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf<List<VaultItem>?>(null) }

    fun reload() { items = store.list() }
    LaunchedEffect(Unit) { reload() }

    fun restore(list: List<VaultItem>) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { list.count { store.restore(it) } }
            Toast.makeText(ctx, "$ok item(s) restored to gallery", Toast.LENGTH_SHORT).show()
            selection = emptySet(); reload()
        }
    }

    BackHandler(viewing != null) { viewing = null }
    BackHandler(viewing == null && selection.isNotEmpty()) { selection = emptySet() }
    BackHandler(viewing == null && selection.isEmpty(), onClose)

    Scaffold(
        topBar = {
            if (selection.isNotEmpty()) {
                val sel = items.filter { it.id in selection }
                TopAppBar(
                    title = { Text("${sel.size} selected") },
                    navigationIcon = { IconButton({ selection = emptySet() }) { Icon(Icons.Filled.Close, "Cancel") } },
                    actions = {
                        IconButton({ restore(sel) }) { Icon(Icons.Filled.Restore, "Restore") }
                        IconButton({ confirmDelete = sel }) { Icon(Icons.Filled.Delete, "Delete") }
                    }
                )
            } else TopAppBar(
                title = { Text("Private vault") },
                navigationIcon = { IconButton(onClose) { Icon(Icons.Filled.ArrowBack, "Back") } }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (items.isEmpty()) {
                Text(
                    "Vault is empty.\nSelect photos in the gallery and tap the lock icon to hide them here.",
                    Modifier.align(Alignment.Center).padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else LazyVerticalGrid(
                columns = GridCells.Fixed(3), contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(items, key = { it.id }) { it0 ->
                    val selected = it0.id in selection
                    Box(
                        Modifier.aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant)
                            .combinedClickable(
                                onClick = {
                                    if (selection.isNotEmpty()) selection = if (selected) selection - it0.id else selection + it0.id
                                    else viewing = items.indexOf(it0)
                                },
                                onLongClick = { selection = if (selected) selection - it0.id else selection + it0.id }
                            )
                    ) {
                        VaultThumb(store, it0, Modifier.fillMaxSize())
                        if (it0.isVideo) Icon(Icons.Filled.PlayArrow, null, tint = Color.White,
                            modifier = Modifier.align(Alignment.Center).size(30.dp))
                        if (selected) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(22.dp))
                    }
                }
            }
        }
    }

    viewing?.let { idx ->
        VaultViewer(
            store = store, items = items, start = idx.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
            onRestore = { restore(listOf(it)); viewing = null },
            onDelete = { confirmDelete = listOf(it) },
            onClose = { viewing = null }
        )
    }

    confirmDelete?.let { list ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${list.size} item(s) permanently?") },
            text = { Text("They will be removed from the vault and cannot be recovered.") },
            confirmButton = {
                TextButton({
                    list.forEach { store.delete(it.id) }
                    confirmDelete = null; selection = emptySet(); viewing = null; reload()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton({ confirmDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun VaultThumb(store: VaultStore, item: VaultItem, modifier: Modifier) {
    val ctx = LocalContext.current
    val bytes by produceState<ByteArray?>(null, item.id) { value = withContext(Dispatchers.IO) { store.readThumb(item.id) } }
    bytes?.let {
        AsyncImage(
            model = coil.request.ImageRequest.Builder(ctx).data(ByteBuffer.wrap(it)).memoryCacheKey("vt_${item.id}").build(),
            contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultViewer(
    store: VaultStore, items: List<VaultItem>, start: Int,
    onRestore: (VaultItem) -> Unit, onDelete: (VaultItem) -> Unit, onClose: () -> Unit
) {
    if (items.isEmpty()) { LaunchedEffect(Unit) { onClose() }; return }
    val pager = rememberPagerState(initialPage = start) { items.size }
    var bars by remember { mutableStateOf(true) }
    val current = items[pager.currentPage.coerceIn(0, items.size - 1)]

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { items.getOrNull(it)?.id ?: it }) { p ->
            val item = items.getOrNull(p)
            if (item != null) {
                if (item.isVideo) {
                    if (pager.currentPage == p) VaultVideo(store, item) { bars = !bars }
                } else {
                    val bytes by produceState<ByteArray?>(null, item.id) { value = withContext(Dispatchers.IO) { store.readFull(item.id) } }
                    bytes?.let { b -> ZoomableImage(ByteBuffer.wrap(b), item.name) { bars = !bars } }
                }
            }
        }
        if (bars) {
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f))
                    .padding(top = 28.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClose) { Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White) }
                Text(current.name, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
                IconButton({ onRestore(current) }) { Icon(Icons.Filled.Restore, "Restore", tint = Color.White) }
                IconButton({ onDelete(current) }) { Icon(Icons.Filled.Delete, "Delete", tint = Color.White) }
            }
        }
    }
}

@Composable
private fun VaultVideo(store: VaultStore, item: VaultItem, onTap: () -> Unit) {
    val uri by produceState<Uri?>(null, item.id) {
        value = withContext(Dispatchers.IO) { store.decryptToCache(item.id)?.let { Uri.fromFile(it) } }
    }
    DisposableEffect(item.id) { onDispose { store.clearCache() } }
    uri?.let { VideoPlayerView(it, onTap) }
}
