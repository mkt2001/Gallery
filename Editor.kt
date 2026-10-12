package com.mahesh.gallerypro.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.mahesh.gallerypro.data.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

// ---------------- bitmap helpers ----------------

/** Decodes an image (honouring EXIF rotation) scaled so the longest side is <= maxSide. */
fun loadBitmap(ctx: Context, uri: Uri, maxSide: Int): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= 28) {
        val src = ImageDecoder.createSource(ctx.contentResolver, uri)
        ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val s = max(info.size.width, info.size.height)
            if (s > maxSide) {
                val f = maxSide.toFloat() / s
                decoder.setTargetSize(
                    (info.size.width * f).toInt().coerceAtLeast(1),
                    (info.size.height * f).toInt().coerceAtLeast(1)
                )
            }
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        val orientation = ctx.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val deg = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (raw != null && deg != 0f) {
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(deg) }, true)
        } else raw
    }
} catch (e: Exception) { null }

private fun transform(b: Bitmap, m: Matrix): Bitmap = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)

private fun cropToRatio(b: Bitmap, rw: Float, rh: Float): Bitmap {
    val target = rw / rh
    val cur = b.width.toFloat() / b.height
    val w: Int
    val h: Int
    if (cur > target) { h = b.height; w = (b.height * target).toInt() }
    else { w = b.width; h = (b.width / target).toInt() }
    return Bitmap.createBitmap(b, (b.width - w) / 2, (b.height - h) / 2, w.coerceAtLeast(1), h.coerceAtLeast(1))
}

// ---------------- filters ----------------

enum class PhotoFilter(val label: String) {
    NONE("Original"), BW("B&W"), SEPIA("Sepia"), VINTAGE("Vintage"),
    COOL("Cool"), WARM("Warm"), VIVID("Vivid"), NEGATIVE("Negative")
}

private fun filterMatrix(f: PhotoFilter): ColorMatrix = when (f) {
    PhotoFilter.NONE -> ColorMatrix()
    PhotoFilter.BW -> ColorMatrix().apply { setSaturation(0f) }
    PhotoFilter.SEPIA -> ColorMatrix(floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f))
    PhotoFilter.VINTAGE -> ColorMatrix(floatArrayOf(
        0.9f, 0.1f, 0f, 0f, 10f,
        0f, 0.85f, 0.1f, 0f, 5f,
        0f, 0.1f, 0.7f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f))
    PhotoFilter.COOL -> ColorMatrix(floatArrayOf(
        0.9f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1.15f, 0f, 10f,
        0f, 0f, 0f, 1f, 0f))
    PhotoFilter.WARM -> ColorMatrix(floatArrayOf(
        1.15f, 0f, 0f, 0f, 10f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 0.85f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f))
    PhotoFilter.VIVID -> ColorMatrix().apply { setSaturation(1.6f) }
    PhotoFilter.NEGATIVE -> ColorMatrix(floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f))
}

/** brightness -100..100, contrast 0.5..1.5, saturation 0..2 */
fun buildMatrix(filter: PhotoFilter, brightness: Float, contrast: Float, saturation: Float): ColorMatrix {
    val m = ColorMatrix(filterMatrix(filter))
    m.postConcat(ColorMatrix().apply { setSaturation(saturation) })
    val t = (1f - contrast) * 127.5f + brightness * 1.5f
    m.postConcat(ColorMatrix(floatArrayOf(
        contrast, 0f, 0f, 0f, t,
        0f, contrast, 0f, 0f, t,
        0f, 0f, contrast, 0f, t,
        0f, 0f, 0f, 1f, 0f)))
    return m
}

private fun render(src: Bitmap, matrix: ColorMatrix): Bitmap {
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) }
    Canvas(out).drawBitmap(src, 0f, 0f, paint)
    return out
}

private fun saveToGallery(ctx: Context, bmp: Bitmap, baseName: String): Boolean = try {
    val name = "${baseName.substringBeforeLast('.')}_edit_${System.currentTimeMillis() / 1000}.jpg"
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/GalleryPro")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        } else {
            val d = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "GalleryPro").apply { mkdirs() }
            put(MediaStore.MediaColumns.DATA, File(d, name).absolutePath)
        }
    }
    val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
    ctx.contentResolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    if (Build.VERSION.SDK_INT >= 29) {
        ctx.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }
    true
} catch (e: Exception) { false }

// ---------------- UI ----------------

private enum class Tool { FILTERS, ADJUST, CROP }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(media: Media, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var filter by remember { mutableStateOf(PhotoFilter.NONE) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var tool by remember { mutableStateOf(Tool.FILTERS) }
    var saving by remember { mutableStateOf(false) }

    BackHandler(onBack = onClose)
    LaunchedEffect(media.key) {
        val b = withContext(Dispatchers.IO) { loadBitmap(ctx, media.uri, 2048) }
        original = b; bitmap = b
        if (b == null) { Toast.makeText(ctx, "Cannot open image", Toast.LENGTH_SHORT).show(); onClose() }
    }

    val matrix = buildMatrix(filter, brightness, contrast, saturation)
    val composeFilter = ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(matrix.array))

    fun edit(block: (Bitmap) -> Bitmap) {
        val b = bitmap ?: return
        scope.launch { bitmap = withContext(Dispatchers.Default) { block(b) } }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        TopAppBar(
            title = { Text("Edit photo") },
            navigationIcon = { IconButton(onClose) { Icon(Icons.Filled.ArrowBack, "Back") } },
            actions = {
                IconButton({
                    bitmap = original; filter = PhotoFilter.NONE
                    brightness = 0f; contrast = 1f; saturation = 1f
                }) { Icon(Icons.Filled.Refresh, "Reset") }
                TextButton(
                    enabled = !saving && bitmap != null,
                    onClick = {
                        val b = bitmap ?: return@TextButton
                        saving = true
                        scope.launch {
                            val ok = withContext(Dispatchers.Default) {
                                saveToGallery(ctx, render(b, matrix), media.name)
                            }
                            saving = false
                            Toast.makeText(ctx, if (ok) "Saved as a copy in Pictures/GalleryPro" else "Save failed", Toast.LENGTH_LONG).show()
                            if (ok) onClose()
                        }
                    }
                ) { Icon(Icons.Filled.Check, null); Text(" Save") }
            }
        )

        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
            val b = bitmap
            if (b == null || saving) CircularProgressIndicator()
            else Image(
                bitmap = b.asImageBitmap(), contentDescription = null,
                contentScale = ContentScale.Fit, colorFilter = composeFilter,
                modifier = Modifier.fillMaxSize()
            )
        }

        // tool tabs
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tool.values().forEach { t ->
                FilterChip(selected = tool == t, onClick = { tool = t },
                    label = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) })
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 16.dp)) {
            when (tool) {
                Tool.FILTERS -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhotoFilter.values().forEach { f ->
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                    }
                }
                Tool.ADJUST -> {
                    LabeledSlider("Brightness", brightness, -100f..100f) { brightness = it }
                    LabeledSlider("Contrast", contrast, 0.5f..1.5f) { contrast = it }
                    LabeledSlider("Saturation", saturation, 0f..2f) { saturation = it }
                }
                Tool.CROP -> {
                    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton({ edit { transform(it, Matrix().apply { postRotate(-90f) }) } }) { Icon(Icons.Filled.RotateLeft, "Rotate left") }
                        IconButton({ edit { transform(it, Matrix().apply { postRotate(90f) }) } }) { Icon(Icons.Filled.RotateRight, "Rotate right") }
                        IconButton({ edit { transform(it, Matrix().apply { postScale(-1f, 1f) }) } }) { Icon(Icons.Filled.Flip, "Flip") }
                        listOf("1:1" to (1f to 1f), "4:3" to (4f to 3f), "3:4" to (3f to 4f), "16:9" to (16f to 9f), "9:16" to (9f to 16f)).forEach { (l, r) ->
                            TextButton({ edit { cropToRatio(it, r.first, r.second) } }) { Text(l) }
                        }
                    }
                    Text("Crop keeps the centre of the photo. Use Reset to undo.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.padding(end = 12.dp).weight(0.35f), style = MaterialTheme.typography.labelLarge)
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(0.65f))
    }
}
