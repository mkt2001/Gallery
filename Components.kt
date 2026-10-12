package com.mahesh.gallerypro.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mahesh.gallerypro.data.Album
import com.mahesh.gallerypro.data.Media

sealed interface Cell {
    data class Header(val label: String, val keys: List<Long>) : Cell
    data class Item(val media: Media) : Cell
}

fun buildCells(items: List<Media>, grouped: Boolean): List<Cell> {
    if (!grouped) return items.map { Cell.Item(it) }
    val out = ArrayList<Cell>(items.size + 32)
    items.groupBy { dayLabel(it.dateTaken) }.forEach { (label, list) ->
        out += Cell.Header(label, list.map { it.key })
        list.forEach { out += Cell.Item(it) }
    }
    return out
}

@Composable
fun MediaGrid(
    items: List<Media>,
    columns: Int,
    grouped: Boolean,
    selection: Set<Long>,
    favorites: Set<Long>,
    onClick: (Media) -> Unit,
    onLongClick: (Media) -> Unit,
    onHeaderClick: (List<Long>) -> Unit,
    modifier: Modifier = Modifier
) {
    val cells = remember(items, grouped) { buildCells(items, grouped) }
    val selecting = selection.isNotEmpty()
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(
            cells,
            key = { if (it is Cell.Header) "h${it.label}" else "m${(it as Cell.Item).media.key}" },
            span = { if (it is Cell.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) }
        ) { cell ->
            when (cell) {
                is Cell.Header -> Text(
                    cell.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onHeaderClick(cell.keys) }
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                )
                is Cell.Item -> Thumb(
                    m = cell.media,
                    selected = cell.media.key in selection,
                    selecting = selecting,
                    fav = cell.media.key in favorites,
                    onClick = { onClick(cell.media) },
                    onLong = { onLongClick(cell.media) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Thumb(
    m: Media,
    selected: Boolean,
    selecting: Boolean,
    fav: Boolean,
    onClick: () -> Unit,
    onLong: () -> Unit
) {
    val ctx = LocalContext.current
    val request = remember(m.uri) { ImageRequest.Builder(ctx).data(m.uri).size(300).build() }
    val scale by animateFloatAsState(if (selected) 0.82f else 1f, label = "thumbScale")
    Box(
        Modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
    ) {
        AsyncImage(
            model = request,
            contentDescription = m.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = scale; scaleY = scale }
        )
        if (m.isVideo) {
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(formatDuration(m.duration), color = Color.White, fontSize = 11.sp)
            }
        }
        if (fav) {
            Icon(
                Icons.Filled.Favorite, null, tint = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).size(16.dp)
            )
        }
        if (selecting) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color.White else Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        Icons.Filled.CheckCircle, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AlbumsGrid(albums: List<Album>, onClick: (Album) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(albums, key = { it.bucketId }) { a ->
            val ctx = LocalContext.current
            val req = remember(a.cover.uri) { ImageRequest.Builder(ctx).data(a.cover.uri).size(400).build() }
            Column(Modifier.clickable { onClick(a) }) {
                AsyncImage(
                    model = req,
                    contentDescription = a.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Text(
                    a.name, maxLines = 1, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text(
                    "${a.count} items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
