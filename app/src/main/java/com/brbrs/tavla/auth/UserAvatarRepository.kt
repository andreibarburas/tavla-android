package com.brbrs.tavla.auth

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches a Nextcloud user's own profile photo (the one they set for themselves on the
 * server, shown everywhere else in Nextcloud) for use as an assignee/commenter avatar, via
 * the standard DAV avatars endpoint -- the same auth as every other Tavla request.
 *
 * Deliberately app-lifetime-cached by uid in memory: avatars rarely change, this is called
 * from every board/card list row, and there's no occasion where a stale photo for a few
 * minutes matters enough to justify a network round trip per recomposition.
 */
@Singleton
class UserAvatarRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val httpClient: OkHttpClient,
) {
    private val cache = ConcurrentHashMap<String, Bitmap?>()

    suspend fun avatarFor(uid: String): Bitmap? {
        cache[uid]?.let { return it }
        if (cache.containsKey(uid)) return null // cached miss (no photo / fetch failed)

        val result = withContext(Dispatchers.IO) {
            val session = authRepository.currentSession() ?: return@withContext null
            runCatching {
                val url = "${session.serverUrl.trimEnd('/')}/remote.php/dav/avatars/${Uri.encode(uid)}/64.png"
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", session.basicAuthHeader())
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.byteStream()?.use { BitmapFactory.decodeStream(it) }
                }
            }.getOrNull()
        }
        cache[uid] = result
        return result
    }
}
