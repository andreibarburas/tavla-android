package com.brbrs.tavla.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Low-level helpers for board cover images (#02). A picked image is downscaled and re-encoded as
 * JPEG, uploaded to the user's own Nextcloud (so covers follow them across devices), and cached on
 * the device for instant rendering. [BoardCoverRepository] orchestrates the upload/download/cache
 * lifecycle; this object only does the image work and owns the cache-path scheme.
 *
 * The cache filename and remote path both carry a per-change token, so when a board's cover is
 * replaced on another device the new token forces a fresh download rather than reusing a stale
 * cached file.
 */
object BoardCovers {

    private const val TAG = "BoardCovers"
    private const val CACHE_DIR = "board_covers"
    private const val MAX_EDGE = 1080      // longest side kept, in pixels
    private const val JPEG_QUALITY = 85

    const val REMOTE_FOLDER = "Tavla/covers"

    fun remotePath(boardId: Long, token: String): String = "$REMOTE_FOLDER/${boardId}_$token.jpg"

    private fun cacheDir(context: Context): File =
        File(context.filesDir, CACHE_DIR).apply { if (!exists()) mkdirs() }

    fun cacheFile(context: Context, boardId: Long, token: String): File =
        File(cacheDir(context), "${boardId}_$token.jpg")

    /** The cached file for a board/token if present on this device, else null. */
    fun cachedOrNull(context: Context, boardId: Long, token: String): File? =
        cacheFile(context, boardId, token).takeIf { it.exists() }

    fun writeCache(context: Context, boardId: Long, token: String, bytes: ByteArray): File =
        cacheFile(context, boardId, token).apply { writeBytes(bytes) }

    /** Delete every cached file for a board (all tokens), e.g. when the cover is removed or replaced. */
    fun clearCacheFor(context: Context, boardId: Long) {
        runCatching { cacheDir(context).listFiles { f -> f.name.startsWith("${boardId}_") }?.forEach { it.delete() } }
    }

    /**
     * Read [uri], downscale it to ~[MAX_EDGE] on the longest edge, apply EXIF rotation, and return
     * JPEG bytes ready to upload and cache. Null if the image can't be read or decoded.
     */
    fun encode(context: Context, uri: Uri): ByteArray? = runCatching {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE)
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null

        val rotation = resolver.openInputStream(uri)?.use { readExifRotation(it) } ?: 0
        val oriented = if (rotation != 0) {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
                .also { if (it != decoded) decoded.recycle() }
        } else decoded

        val scaled = scaleToMaxEdge(oriented, MAX_EDGE)
        if (scaled != oriented) oriented.recycle()

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        scaled.recycle()
        out.toByteArray()
    }.getOrElse {
        Log.w(TAG, "Failed to encode board cover: ${it.message}")
        null
    }

    private fun sampleSize(width: Int, height: Int, target: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= target && h / 2 >= target) {
            w /= 2; h /= 2; sample *= 2
        }
        return sample
    }

    private fun scaleToMaxEdge(bmp: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bmp.width, bmp.height)
        if (longest <= maxEdge) return bmp
        val ratio = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(bmp, (bmp.width * ratio).toInt(), (bmp.height * ratio).toInt(), true)
    }

    private fun readExifRotation(input: java.io.InputStream): Int = runCatching {
        when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)
}
