package com.mahesh.gallerypro.data

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MediaRepository(app.contentResolver)
    private val prefs = app.getSharedPreferences("gallery", Context.MODE_PRIVATE)

    var media by mutableStateOf<List<Media>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var hasPermission by mutableStateOf(false); private set

    var favorites by mutableStateOf(
        prefs.getStringSet("fav", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
    ); private set
    var sort by mutableStateOf(
        runCatching { SortOrder.valueOf(prefs.getString("sort", "NEWEST")!!) }.getOrDefault(SortOrder.NEWEST)
    ); private set
    var columns by mutableIntStateOf(prefs.getInt("cols", 3)); private set
    var theme by mutableStateOf(prefs.getString("theme", "SYSTEM")!!); private set

    var trash by mutableStateOf<List<Media>>(emptyList()); private set
    var filter by mutableStateOf(MediaFilter.ALL); private set
    var query by mutableStateOf(""); private set
    var selection by mutableStateOf<Set<Long>>(emptySet()); private set

    private var refreshJob: Job? = null
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    init {
        val r = app.contentResolver
        r.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        r.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }

    // ---------- derived lists ----------
    fun arrange(list: List<Media>): List<Media> {
        val q = query.trim()
        val f = if (q.isEmpty()) list else list.filter {
            it.name.contains(q, true) || it.bucketName.contains(q, true)
        }
        return when (sort) {
            SortOrder.NEWEST -> f.sortedByDescending { it.dateTaken }
            SortOrder.OLDEST -> f.sortedBy { it.dateTaken }
            SortOrder.NAME -> f.sortedBy { it.name.lowercase() }
            SortOrder.SIZE -> f.sortedByDescending { it.size }
        }
    }

    val shown: List<Media> by derivedStateOf {
        arrange(media.filter {
            when (filter) {
                MediaFilter.ALL -> true
                MediaFilter.PHOTOS -> !it.isVideo
                MediaFilter.VIDEOS -> it.isVideo
            }
        })
    }

    val albums: List<Album> by derivedStateOf {
        val q = query.trim()
        media.groupBy { it.bucketId }
            .map { (id, list) ->
                Album(id, list.first().bucketName, list.maxBy { it.dateTaken }, list.size)
            }
            .filter { q.isEmpty() || it.name.contains(q, true) }
            .sortedByDescending { it.cover.dateTaken }
    }

    val favoriteItems: List<Media> by derivedStateOf { media.filter { it.key in favorites } }

    // ---------- loading ----------
    fun setPermission(granted: Boolean) {
        hasPermission = granted
        if (granted) refresh() else loading = false
    }

    fun refresh() {
        if (!hasPermission) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(300)
            val result = withContext(Dispatchers.IO) { repo.load() }
            media = result
            loading = false
            val keys = result.mapTo(HashSet()) { it.key }
            selection = selection.filter { it in keys }.toSet()
        }
    }

    fun loadTrash() {
        viewModelScope.launch {
            trash = withContext(Dispatchers.IO) { repo.loadTrash() }
        }
    }

    // ---------- settings ----------
    fun setSort(s: SortOrder) { sort = s; prefs.edit().putString("sort", s.name).apply() }
    fun setColumns(c: Int) { columns = c; prefs.edit().putInt("cols", c).apply() }
    fun setTheme(t: String) { theme = t; prefs.edit().putString("theme", t).apply() }
    fun setFilter(f: MediaFilter) { filter = f }
    fun setQuery(q: String) { query = q }

    // ---------- favorites ----------
    private fun saveFavorites() {
        prefs.edit().putStringSet("fav", favorites.map { it.toString() }.toSet()).apply()
    }

    fun toggleFavorite(m: Media) {
        favorites = if (m.key in favorites) favorites - m.key else favorites + m.key
        saveFavorites()
    }

    fun favoriteSelected() {
        val sel = selection
        favorites = if (sel.all { it in favorites }) favorites - sel else favorites + sel
        saveFavorites()
        clearSelection()
    }

    // ---------- selection ----------
    fun toggleSelect(key: Long) {
        selection = if (key in selection) selection - key else selection + key
    }
    fun selectAll(keys: Collection<Long>) { selection = selection + keys }
    fun clearSelection() { selection = emptySet() }
}
