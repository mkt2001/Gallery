package com.mahesh.gallerypro.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class VaultItem(
    val id: String,
    val name: String,
    val mime: String,
    val isVideo: Boolean,
    val date: Long,
    val size: Long
)

/**
 * Everything in the vault (files, thumbnails, index, PIN hash) is encrypted with an
 * AES-256 key that lives in the Android Keystore. Files sit in app-private storage.
 */
class VaultStore(private val ctx: Context) {

    private val masterKey = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val dir = File(ctx.filesDir, "vault").apply { mkdirs() }

    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            ctx, "vault_secure", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ---------------- PIN ----------------
    val isPinSet: Boolean get() = prefs.contains("pin_hash")

    var biometricEnabled: Boolean
        get() = prefs.getBoolean("bio", true)
        set(v) { prefs.edit().putBoolean("bio", v).apply() }

    private fun hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", hash(pin, salt))
            .putInt("fails", 0).putLong("lock_until", 0)
            .apply()
    }

    /** Remaining lock-out seconds after too many wrong attempts. */
    fun lockedSeconds(): Long = ((prefs.getLong("lock_until", 0) - System.currentTimeMillis()) / 1000).coerceAtLeast(0)

    fun checkPin(pin: String): Boolean {
        if (lockedSeconds() > 0) return false
        val salt = Base64.decode(prefs.getString("pin_salt", "") ?: "", Base64.NO_WRAP)
        val expected = prefs.getString("pin_hash", "") ?: ""
        val ok = MessageDigest.isEqual(hash(pin, salt).toByteArray(), expected.toByteArray())
        if (ok) {
            prefs.edit().putInt("fails", 0).apply()
        } else {
            val fails = prefs.getInt("fails", 0) + 1
            val e = prefs.edit().putInt("fails", fails)
            if (fails % 5 == 0) e.putLong("lock_until", System.currentTimeMillis() + 30_000L * (fails / 5))
            e.apply()
        }
        return ok
    }

    // ---------------- items ----------------
    private fun enc(f: File) = EncryptedFile.Builder(
        ctx, f, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
    ).build()

    private fun writeEnc(f: File, bytes: ByteArray) {
        if (f.exists()) f.delete()
        enc(f).openFileOutput().use { it.write(bytes) }
    }

    private fun readEnc(f: File): ByteArray = enc(f).openFileInput().use { it.readBytes() }

    fun list(): List<VaultItem> =
        prefs.all.keys.filter { it.startsWith("item_") }.mapNotNull { k ->
            val parts = (prefs.getString(k, null) ?: return@mapNotNull null).split("\u0001")
            if (parts.size < 5) null
            else VaultItem(k.removePrefix("item_"), parts[0], parts[1], parts[2] == "1",
                parts[3].toLongOrNull() ?: 0L, parts[4].toLongOrNull() ?: 0L)
        }.sortedByDescending { it.date }

    private fun thumbOf(m: Media): ByteArray? {
        return try {
            val bmp: Bitmap? = if (m.isVideo) {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(ctx, m.uri)
                    r.getFrameAtTime(0)
                } finally { r.release() }
            } else {
                com.mahesh.gallerypro.ui.loadBitmap(ctx, m.uri, 320)
            }
            bmp?.let {
                val out = ByteArrayOutputStream()
                it.compress(Bitmap.CompressFormat.JPEG, 80, out)
                out.toByteArray()
            }
        } catch (e: Exception) { null }
    }

    /** Copies (encrypts) a gallery item into the vault. Returns true on success. */
    fun add(m: Media): Boolean = runCatching { addInternal(m) }.getOrDefault(false)

    private fun addInternal(m: Media): Boolean {
        val id = java.util.UUID.randomUUID().toString().replace("-", "")
        val data = ctx.contentResolver.openInputStream(m.uri)?.use { it.readBytes() } ?: return false
        writeEnc(File(dir, "$id.dat"), data)
        thumbOf(m)?.let { writeEnc(File(dir, "$id.thm"), it) }
        val meta = listOf(m.name, m.mime, if (m.isVideo) "1" else "0", m.dateTaken, data.size).joinToString("\u0001")
        prefs.edit().putString("item_$id", meta).apply()
        return true
    }

    fun readThumb(id: String): ByteArray? =
        File(dir, "$id.thm").takeIf { it.exists() }?.let { runCatching { readEnc(it) }.getOrNull() }

    fun readFull(id: String): ByteArray? =
        File(dir, "$id.dat").takeIf { it.exists() }?.let { runCatching { readEnc(it) }.getOrNull() }

    /** Decrypts a video into the cache so the player can read it. Caller must delete it afterwards. */
    fun decryptToCache(id: String): File? {
        val bytes = readFull(id) ?: return null
        val f = File(ctx.cacheDir, "vault_play_$id.tmp")
        f.writeBytes(bytes)
        return f
    }

    fun clearCache() {
        ctx.cacheDir.listFiles()?.filter { it.name.startsWith("vault_play_") }?.forEach { it.delete() }
    }

    fun delete(id: String) {
        File(dir, "$id.dat").delete()
        File(dir, "$id.thm").delete()
        prefs.edit().remove("item_$id").apply()
    }

    /** Decrypts the item back into the public gallery (Pictures|Movies/Restored) and removes it from the vault. */
    fun restore(item: VaultItem): Boolean = runCatching { restoreInternal(item) }.getOrDefault(false)

    private fun restoreInternal(item: VaultItem): Boolean {
        val bytes = readFull(item.id) ?: return false
        val base = if (item.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val folder = if (item.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mime)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/Restored")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else {
                val d = File(Environment.getExternalStoragePublicDirectory(folder), "Restored").apply { mkdirs() }
                put(MediaStore.MediaColumns.DATA, File(d, item.name).absolutePath)
            }
        }
        val uri: Uri = ctx.contentResolver.insert(base, values) ?: return false
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return false
        if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            ctx.contentResolver.update(uri, v, null, null)
        }
        delete(item.id)
        return true
    }
}
