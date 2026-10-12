package com.mahesh.gallerypro.ui

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.exifinterface.media.ExifInterface
import androidx.media3.common.MediaItem as ExoItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mahesh.gallerypro.data.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class ViewerRequest(val items: List<Media>, val startKey: Long)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Viewer(
    items: List<Media>,
    startKey: Long,
    favorites: Set<Long>,
    onFavorite: (Media) -> Unit,
    onShare: (Media) -> Unit,
    onDelete: (Media) -> Unit,
    onEdit: (Media) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val start = items.indexOfFirst { it.key == startKey }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start) { items.size }
    var bars by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    var slideshow by remember { mutableStateOf(false) }
    val page = pager.currentPage.coerceIn(0, items.size - 1)
    val current = items[page]

    LaunchedEffect(slideshow, pager.currentPage) {
        if (slideshow) {
            if (items.getOrNull(pager.currentPage)?.isVideo == true) { slideshow = false; return@LaunchedEffect }
            delay(3000)
            if (pager.currentPage < items.size - 1) pager.animateScrollToPage(pager.currentPage + 1)
            else slideshow = false
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            key = { items.getOrNull(it)?.key ?: it }
        ) { p ->
            val m = items.getOrNull(p)
            if (m != null) {
                if (m.isVideo) VideoPage(m, active = pager.currentPage == p) { bars = !bars }
                else ZoomableImage(m) { bars = !bars }
            }
        }

        AnimatedVisibility(visible = bars, modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClose) { Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(dayLabel(current.dateTaken), color = Color.White, style = MaterialTheme.typography.titleSmall)
                    Text(current.name, color = Color.White.copy(alpha = 0.75f), maxLines = 1, style = MaterialTheme.typography.bodySmall)
                }
                IconButton({ onFavorite(current) }) {
                    val fav = current.key in favorites
                    Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorite", tint = Color.White)
                }
                if (!current.isVideo) {
                    IconButton({ onEdit(current) }) { Icon(Icons.Filled.Edit, "Edit", tint = Color.White) }
                }
                IconButton({ onShare(current) }) { Icon(Icons.Filled.Share, "Share", tint = Color.White) }
                IconButton({ showInfo = true }) { Icon(Icons.Filled.Info, "Details", tint = Color.White) }
                IconButton({ onDelete(current) }) { Icon(Icons.Filled.Delete, "Delete", tint = Color.White) }
            }
        }

        AnimatedVisibility(visible = bars, modifier = Modifier.align(Alignment.BottomCenter)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${page + 1} / ${items.size}", color = Color.White)
                IconButton({ slideshow = !slideshow }) {
                    Icon(
                        if (slideshow) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        "Slideshow", tint = Color.White
                    )
                }
            }
        }
    }

    if (showInfo) InfoSheet(current) { showInfo = false }
}

@Composable
fun ZoomableImage(m: Media, onTap: () -> Unit) = ZoomableImage(m.uri, m.name, onTap)

@Composable
fun ZoomableImage(model: Any, desc: String, onTap: () -> Unit) {
    val ctx = LocalContext.current
    val request = remember(model) { ImageRequest.Builder(ctx).data(model).size(2048).build() }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(o: Offset, s: Float): Offset {
        val mx = size.width * (s - 1f) / 2f
        val my = size.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val ev = awaitPointerEvent()
                        val zoom = ev.calculateZoom()
                        val pan = ev.calculatePan()
                        if (ev.changes.size > 1 || scale > 1f) {
                            val ns = (scale * zoom).coerceIn(1f, 6f)
                            scale = ns
                            offset = if (ns > 1f) clamp(offset + pan, ns) else Offset.Zero
                            ev.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (ev.changes.any { it.pressed })
                }
            }
    ) {
        AsyncImage(
            model = request,
            contentDescription = desc,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = offset.x; translationY = offset.y
                }
        )
    }
}

@Composable
fun VideoPage(m: Media, active: Boolean, onTap: () -> Unit) {
    val ctx = LocalContext.current
    if (!active) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val req = remember(m.uri) { ImageRequest.Builder(ctx).data(m.uri).size(1080).build() }
            AsyncImage(req, m.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(64.dp))
        }
    } else {
        VideoPlayerView(m.uri, onTap)
    }
}

@Composable
fun VideoPlayerView(uri: Uri, onTap: () -> Unit) {
    val ctx = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(ExoItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { c ->
            PlayerView(c).apply {
                this.player = player
                useController = true
                setOnClickListener { onTap() }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSheet(m: Media, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var exif by remember(m.key) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(m.key) {
        if (!m.isVideo) exif = withContext(Dispatchers.IO) { readExif(ctx, m.uri) }
    }
    val rows = buildList {
        add("Name" to m.name)
        add("Date" to fullDate(m.dateTaken))
        add("Size" to formatSize(m.size))
        if (m.width > 0) add("Resolution" to "${m.width} x ${m.height}  (${"%.1f".format(m.width * m.height / 1_000_000f)} MP)")
        if (m.isVideo) add("Duration" to formatDuration(m.duration))
        add("Type" to m.mime)
        add("Album" to m.bucketName)
        m.path?.let { add("Path" to it) }
        addAll(exif)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("Details", style = MaterialTheme.typography.titleMedium)
            rows.forEach { (k, v) ->
                Column(Modifier.padding(top = 12.dp)) {
                    Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(v, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun readExif(ctx: Context, uri: Uri): List<Pair<String, String>> {
    fun open(u: Uri) = ctx.contentResolver.openInputStream(u)
    val stream = try {
        if (Build.VERSION.SDK_INT >= 29) open(MediaStore.setRequireOriginal(uri)) else open(uri)
    } catch (e: Exception) {
        try { open(uri) } catch (e2: Exception) { null }
    } ?: return emptyList()
    return try {
        stream.use {
            val e = ExifInterface(it)
            buildList {
                val camera = listOfNotNull(e.getAttribute(ExifInterface.TAG_MAKE), e.getAttribute(ExifInterface.TAG_MODEL))
                    .joinToString(" ")
                if (camera.isNotBlank()) add("Camera" to camera)
                e.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { add("Aperture" to "f/$it") }
                val t = e.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
                if (t > 0) add("Shutter" to if (t < 1) "1/${(1 / t).roundToInt()} s" else "$t s")
                e.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { add("ISO" to it) }
                e.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let {
                    val parts = it.split("/")
                    val mm = if (parts.size == 2) parts[0].toDoubleOrNull()?.div(parts[1].toDoubleOrNull() ?: 1.0) else it.toDoubleOrNull()
                    if (mm != null) add("Focal length" to "${mm.roundToInt()} mm")
                }
                e.latLong?.let { add("Location" to "%.5f, %.5f".format(it[0], it[1])) }
            }
        }
    } catch (ex: Exception) {
        emptyList()
    }
}
