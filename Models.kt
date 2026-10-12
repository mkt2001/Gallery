package com.mahesh.gallerypro.data

import android.net.Uri

data class Media(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mime: String,
    val isVideo: Boolean,
    val dateTaken: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val duration: Long,
    val bucketId: Long,
    val bucketName: String,
    val path: String?
) {
    /** Unique across images and videos. */
    val key: Long get() = id * 2 + if (isVideo) 1 else 0
}

data class Album(val bucketId: Long, val name: String, val cover: Media, val count: Int)

enum class SortOrder(val label: String) {
    NEWEST("Newest first"), OLDEST("Oldest first"), NAME("Name (A-Z)"), SIZE("Largest first")
}

enum class MediaFilter(val label: String) { ALL("All"), PHOTOS("Photos"), VIDEOS("Videos") }
