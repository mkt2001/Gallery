package com.mahesh.gallerypro

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mahesh.gallerypro.data.VaultStore
import com.mahesh.gallerypro.ui.EditorScreen
import com.mahesh.gallerypro.ui.TrashScreen
import com.mahesh.gallerypro.ui.VaultScreen
import com.mahesh.gallerypro.data.GalleryViewModel
import com.mahesh.gallerypro.data.Media
import com.mahesh.gallerypro.ui.GalleryTheme
import com.mahesh.gallerypro.ui.HomeScreen
import com.mahesh.gallerypro.ui.Viewer
import com.mahesh.gallerypro.ui.ViewerRequest

class MainActivity : FragmentActivity() {

    private val vm: GalleryViewModel by viewModels()
    private val vault by lazy { VaultStore(applicationContext) }
    private var showVaultState by mutableStateOf(false)

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            vm.setPermission(hasMediaAccess())
        }

    private val deleteLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            vm.clearSelection()
            vm.refresh()
            vm.loadTrash()
        }

    private fun requiredPermissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
            if (Build.VERSION.SDK_INT >= 34) add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT < 29) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }.toTypedArray()

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun hasMediaAccess(): Boolean = when {
        Build.VERSION.SDK_INT >= 34 -> granted(Manifest.permission.READ_MEDIA_IMAGES) ||
            granted(Manifest.permission.READ_MEDIA_VIDEO) ||
            granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> granted(Manifest.permission.READ_MEDIA_IMAGES) ||
            granted(Manifest.permission.READ_MEDIA_VIDEO)
        else -> granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm.setPermission(hasMediaAccess())
        if (!hasMediaAccess()) permLauncher.launch(requiredPermissions())
        setContent {
            val dark = when (vm.theme) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }
            GalleryTheme(dark) { Root() }
        }
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app closes the vault: it asks for PIN/fingerprint again next time.
        showVaultState = false
    }

    override fun onResume() {
        super.onResume()
        val ok = hasMediaAccess()
        if (ok != vm.hasPermission) vm.setPermission(ok) else vm.refresh()
    }

    // ---------- actions ----------
    private fun share(list: List<Media>) {
        if (list.isEmpty()) return
        val intent = if (list.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, list[0].uri)
        else Intent(Intent.ACTION_SEND_MULTIPLE)
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(list.map { it.uri }))
        intent.type = when {
            list.size == 1 -> list[0].mime
            list.all { !it.isVideo } -> "image/*"
            list.all { it.isVideo } -> "video/*"
            else -> "*/*"
        }
        val clip = ClipData.newRawUri("media", list[0].uri)
        list.drop(1).forEach { clip.addItem(ClipData.Item(it.uri)) }
        intent.clipData = clip
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, "Share"))
    }

    private fun deleteMedia(uris: List<Uri>, permanent: Boolean = false) {
        if (uris.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 30) {
            val pi = if (permanent) MediaStore.createDeleteRequest(contentResolver, uris)
            else MediaStore.createTrashRequest(contentResolver, uris, true)
            deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } else {
            try {
                uris.forEach { contentResolver.delete(it, null, null) }
                vm.clearSelection()
                vm.refresh()
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= 29 && e is RecoverableSecurityException) {
                    deleteLauncher.launch(
                        IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                    )
                }
            }
        }
    }

    private fun restoreFromTrash(list: List<Media>) {
        if (Build.VERSION.SDK_INT >= 30 && list.isNotEmpty()) {
            val pi = MediaStore.createTrashRequest(contentResolver, list.map { it.uri }, false)
            deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        }
    }

    /** Encrypts the items into the vault, then permanently deletes the originals (not trash). */
    private fun hideInVault(list: List<Media>, openVault: () -> Unit) {
        if (list.isEmpty()) return
        if (!vault.isPinSet) {
            Toast.makeText(this, "Create your vault PIN first", Toast.LENGTH_LONG).show()
            openVault()
            return
        }
        lifecycleScope.launch {
            val done = withContext(Dispatchers.IO) { list.filter { vault.add(it) } }
            if (done.isEmpty()) {
                Toast.makeText(this@MainActivity, "Could not move to vault", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "${done.size} item(s) moved to vault", Toast.LENGTH_SHORT).show()
                deleteMedia(done.map { it.uri }, permanent = true)
            }
        }
    }

    @Composable
    private fun Root() {
        var viewer by remember { mutableStateOf<ViewerRequest?>(null) }
        var editing by remember { mutableStateOf<Media?>(null) }
        var showTrash by remember { mutableStateOf(false) }
        var vaultOpen by remember { mutableStateOf(false) }

        // Block screenshots / hide from recent apps while the vault is unlocked.
        DisposableEffect(vaultOpen) {
            if (vaultOpen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            onDispose { }
        }
        var pendingDelete by remember { mutableStateOf<List<Media>?>(null) }
        val requestDelete: (List<Media>) -> Unit = { list ->
            if (list.isNotEmpty()) {
                // Android 11+ shows its own system confirmation (items go to system Trash).
                if (Build.VERSION.SDK_INT >= 30) deleteMedia(list.map { it.uri }) else pendingDelete = list
            }
        }

        Box(Modifier.fillMaxSize()) {
            HomeScreen(
                vm = vm,
                onOpen = { list, m -> viewer = ViewerRequest(list, m.key) },
                onShare = ::share,
                onDelete = requestDelete,
                onHide = { hideInVault(it) { showVaultState = true } },
                onOpenVault = { showVaultState = true },
                onOpenTrash = { vm.loadTrash(); showTrash = true },
                onGrant = { permLauncher.launch(requiredPermissions()) }
            )
            viewer?.let { req ->
                val live = remember(req, vm.media) {
                    val keys = vm.media.mapTo(HashSet()) { it.key }
                    req.items.filter { it.key in keys }
                }
                Viewer(
                    items = live,
                    startKey = req.startKey,
                    favorites = vm.favorites,
                    onFavorite = vm::toggleFavorite,
                    onShare = { share(listOf(it)) },
                    onDelete = { requestDelete(listOf(it)) },
                    onEdit = { editing = it },
                    onClose = { viewer = null }
                )
            }
            if (showTrash) {
                TrashScreen(
                    vm = vm,
                    onRestore = { restoreFromTrash(it) },
                    onDeleteForever = { deleteMedia(it.map { m -> m.uri }, permanent = true) },
                    onClose = { showTrash = false }
                )
            }
            if (showVaultState) {
                VaultScreen(
                    store = vault,
                    onUnlockedChange = { vaultOpen = it },
                    onClose = { showVaultState = false }
                )
            }
            editing?.let { EditorScreen(it) { editing = null; vm.refresh() } }
        }

        pendingDelete?.let { list ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("Delete ${list.size} item(s)?") },
                text = { Text("This permanently deletes the selected photos/videos from your device.") },
                confirmButton = {
                    TextButton({ pendingDelete = null; deleteMedia(list.map { it.uri }) }) { Text("Delete") }
                },
                dismissButton = { TextButton({ pendingDelete = null }) { Text("Cancel") } }
            )
        }
    }
}
