package com.mahesh.gallerypro.data

import android.content.ContentResolver
import android.content.ContentUris
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore

class MediaRepository(private val resolver: ContentResolver) {

    fun load(): List<Media> {
        val images = runCatching { query(false) }.getOrDefault(emptyList())
        val videos = runCatching { query(true) }.getOrDefault(emptyList())
        return images + videos
    }

    fun loadTrash(): List<Media> {
        if (Build.VERSION.SDK_INT < 30) return emptyList()
        val images = runCatching { query(false, true) }.getOrDefault(emptyList())
        val videos = runCatching { query(true, true) }.getOrDefault(emptyList())
        return images + videos
    }

    private fun query(video: Boolean, trashed: Boolean = false): List<Media> {
        val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val cols = mutableListOf(
            "_id", "_display_name", "mime_type", "date_added", "datetaken", "_size",
            "width", "height", "bucket_id", "bucket_display_name", "_data"
        )
        if (video) cols += "duration"
        val out = ArrayList<Media>()
        val cursor = if (trashed && Build.VERSION.SDK_INT >= 30) {
            val args = Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
                putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "date_added DESC")
            }
            resolver.query(base, cols.toTypedArray(), args, null)
        } else {
            resolver.query(base, cols.toTypedArray(), null, null, "date_added DESC")
        }
        cursor?.use { c ->
            val iId = c.getColumnIndexOrThrow("_id")
            val iName = c.getColumnIndexOrThrow("_display_name")
            val iMime = c.getColumnIndexOrThrow("mime_type")
            val iAdded = c.getColumnIndexOrThrow("date_added")
            val iTaken = c.getColumnIndexOrThrow("datetaken")
            val iSize = c.getColumnIndexOrThrow("_size")
            val iW = c.getColumnIndexOrThrow("width")
            val iH = c.getColumnIndexOrThrow("height")
            val iBucket = c.getColumnIndexOrThrow("bucket_id")
            val iBucketName = c.getColumnIndexOrThrow("bucket_display_name")
            val iData = c.getColumnIndexOrThrow("_data")
            val iDur = if (video) c.getColumnIndexOrThrow("duration") else -1
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val added = c.getLong(iAdded) * 1000
                val taken = if (c.isNull(iTaken)) 0L else c.getLong(iTaken)
                out += Media(
                    id = id,
                    uri = ContentUris.withAppendedId(base, id),
                    name = c.getString(iName) ?: "Unknown",
                    mime = c.getString(iMime) ?: if (video) "video/*" else "image/*",
                    isVideo = video,
                    dateTaken = if (taken > 0) taken else added,
                    size = c.getLong(iSize),
                    width = c.getInt(iW),
                    height = c.getInt(iH),
                    duration = if (video && !c.isNull(iDur)) c.getLong(iDur) else 0L,
                    bucketId = c.getLong(iBucket),
                    bucketName = c.getString(iBucketName) ?: "Unknown",
                    path = c.getString(iData)
                )
            }
        }
        return out
    }
}
