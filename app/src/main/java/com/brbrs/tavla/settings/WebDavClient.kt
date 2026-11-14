package com.brbrs.tavla.settings

import com.brbrs.tavla.auth.TavlaSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** A file read from WebDAV, with the ETag needed to avoid clobbering a concurrent write. */
data class DavFile(val content: String, val etag: String?)

/** Thrown when a conditional PUT loses a race with another device (HTTP 412). Caller should re-fetch and retry. */
class DavConflictException : Exception("The file changed on the server since it was last read")

/**
 * Talks to Nextcloud's WebDAV endpoint directly -- the same protocol the Nextcloud sync client
 * uses -- with the Deck app password Tavla already has. No extra permissions or app needed on
 * the server.
 */
@Singleton
class WebDavClient @Inject constructor(
    private val httpClient: OkHttpClient,
) {
    private fun davRoot(s: TavlaSession) =
        "${s.serverUrl.trimEnd('/')}/remote.php/dav/files/${s.username}"

    /** Creates [folderPath] (one segment, e.g. "Tavla") if it doesn't exist yet. Safe to call every time. */
    suspend fun ensureFolder(s: TavlaSession, folderPath: String): Unit = withContext(Dispatchers.IO) {
        val url = "${davRoot(s)}/${folderPath.trim('/')}/"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", s.basicAuthHeader())
            .method("MKCOL", null)
            .build()
        httpClient.newCall(request).execute().use { response ->
            // 201 = created, 405 = already exists. Anything else (e.g. 401/507) is a real problem.
            if (response.code != 201 && response.code != 405) {
                throw java.io.IOException("Could not create /$folderPath (HTTP ${response.code})")
            }
        }
    }

    /** Returns the file's content and ETag, or null if it doesn't exist yet (first run on this account). */
    suspend fun getFile(s: TavlaSession, path: String): DavFile? = withContext(Dispatchers.IO) {
        val url = "${davRoot(s)}/${path.trimStart('/')}"
        val request = Request.Builder().url(url).header("Authorization", s.basicAuthHeader()).get().build()
        httpClient.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> null
                response.isSuccessful -> DavFile(response.body?.string().orEmpty(), response.header("ETag"))
                else -> throw java.io.IOException("Could not read /$path (HTTP ${response.code})")
            }
        }
    }

    /**
     * Writes [content] to [path]. If [ifMatchEtag] is given and the file changed since, throws
     * [DavConflictException] instead of overwriting another device's edit. Returns the new ETag.
     */
    suspend fun putFile(s: TavlaSession, path: String, content: String, ifMatchEtag: String?): String? =
        withContext(Dispatchers.IO) {
            val url = "${davRoot(s)}/${path.trimStart('/')}"
            val builder = Request.Builder()
                .url(url)
                .header("Authorization", s.basicAuthHeader())
                .put(content.toRequestBody("application/json; charset=utf-8".toMediaType()))
            if (ifMatchEtag != null) builder.header("If-Match", ifMatchEtag)
            httpClient.newCall(builder.build()).execute().use { response ->
                when {
                    response.code == 412 -> throw DavConflictException()
                    response.isSuccessful -> response.header("ETag")
                    else -> throw java.io.IOException("Could not save /$path (HTTP ${response.code})")
                }
            }
        }

    // -- Binary variants, used for board cover images (#02) -------------------------------------

    /** Reads a binary file (e.g. a cover image), or null if it doesn't exist. */
    suspend fun getBytes(s: TavlaSession, path: String): ByteArray? = withContext(Dispatchers.IO) {
        val url = "${davRoot(s)}/${path.trimStart('/')}"
        val request = Request.Builder().url(url).header("Authorization", s.basicAuthHeader()).get().build()
        httpClient.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> null
                response.isSuccessful -> response.body?.bytes()
                else -> throw java.io.IOException("Could not read /$path (HTTP ${response.code})")
            }
        }
    }

    /** Writes binary [bytes] to [path] with the given content type. Overwrites unconditionally. */
    suspend fun putBytes(s: TavlaSession, path: String, bytes: ByteArray, contentType: String): Unit =
        withContext(Dispatchers.IO) {
            val url = "${davRoot(s)}/${path.trimStart('/')}"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", s.basicAuthHeader())
                .put(bytes.toRequestBody(contentType.toMediaType()))
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw java.io.IOException("Could not upload /$path (HTTP ${response.code})")
            }
        }

    /** Deletes [path]. A missing file (404) is treated as success. */
    suspend fun deleteFile(s: TavlaSession, path: String): Unit = withContext(Dispatchers.IO) {
        val url = "${davRoot(s)}/${path.trimStart('/')}"
        val request = Request.Builder().url(url).header("Authorization", s.basicAuthHeader()).delete().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 404) {
                throw java.io.IOException("Could not delete /$path (HTTP ${response.code})")
            }
        }
    }
}
