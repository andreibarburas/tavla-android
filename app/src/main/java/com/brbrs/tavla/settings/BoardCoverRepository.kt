package com.brbrs.tavla.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import com.brbrs.tavla.auth.AuthRepository
import com.brbrs.tavla.di.ApplicationScope
import com.brbrs.tavla.sync.WorkScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns board cover images (#02) end to end: uploads a picked image to the user's own Nextcloud,
 * caches it on the device for instant rendering, and -- crucially -- downloads covers created on
 * another device so they follow the person around. [readyCovers] maps a board id to the absolute
 * path of its cached cover once it is available locally; the UI reads only that, so a board shows
 * its cover the moment the file is on disk and falls back to its icon or glyph until then.
 *
 * The cover reference stored in [TavlaSettings.boardImages] is a per-change token. The remote path
 * and cache filename both embed it, so replacing a cover (new token) forces a fresh download on
 * other devices instead of serving a stale cached file.
 */
@Singleton
class BoardCoverRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val davClient: WebDavClient,
    private val settingsRepository: SettingsRepository,
    private val workScheduler: WorkScheduler,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val TAG = "BoardCovers"

    private val _readyCovers = MutableStateFlow<Map<Long, String>>(emptyMap())
    /** boardId -> absolute path of the cached cover, for boards whose cover is present on this device. */
    val readyCovers: StateFlow<Map<Long, String>> = _readyCovers

    private val downloading = mutableSetOf<Long>()
    private val downloadMutex = Mutex()

    init {
        // Keep the local cache in step with the synced settings: cache hits render immediately,
        // misses (a cover made on another device) are fetched in the background.
        appScope.launch {
            settingsRepository.settings
                .map { it.boardImages }
                .distinctUntilChanged()
                .collect { reconcile(it) }
        }
    }

    private suspend fun reconcile(tokens: Map<Long, String>) {
        // Resolve what's already cached, and drop ready entries for boards no longer covered.
        val ready = mutableMapOf<Long, String>()
        tokens.forEach { (boardId, token) ->
            BoardCovers.cachedOrNull(context, boardId, token)?.let { ready[boardId] = it.absolutePath }
        }
        _readyCovers.value = ready

        // Download any cover we have a token for but no cached file.
        tokens.forEach { (boardId, token) ->
            if (ready.containsKey(boardId)) return@forEach
            downloadMutex.withLock { if (!downloading.add(boardId)) return@forEach }
            appScope.launch {
                try {
                    val session = authRepository.currentSession() ?: return@launch
                    val bytes = davClient.getBytes(session, BoardCovers.remotePath(boardId, token)) ?: return@launch
                    val file = BoardCovers.writeCache(context, boardId, token, bytes)
                    _readyCovers.update { it + (boardId to file.absolutePath) }
                } catch (e: Exception) {
                    Log.w(TAG, "Cover download failed for board $boardId: ${e.message}")
                } finally {
                    downloadMutex.withLock { downloading.remove(boardId) }
                }
            }
        }
    }

    /**
     * Encode, cache and upload a newly picked cover, then point settings at the new token so other
     * devices pick it up. Returns false if the image could not be read. The cache is written first
     * so the tile updates instantly, independent of the upload completing.
     */
    suspend fun setCover(boardId: Long, uri: Uri): Boolean {
        val bytes = BoardCovers.encode(context, uri) ?: return false
        val previousToken = settingsRepository.settings.value.boardImages[boardId]
        val token = System.currentTimeMillis().toString()

        // Clear any older cached files for this board, write the new one, and render it now.
        BoardCovers.clearCacheFor(context, boardId)
        val file = BoardCovers.writeCache(context, boardId, token, bytes)
        _readyCovers.update { it + (boardId to file.absolutePath) }

        // Point settings at the new token (this syncs to Nextcloud), then hand the actual upload to
        // a durable worker -- a bare coroutine here would silently lose the upload (and leave the
        // token pointing at bytes that were never saved) if it failed or the app was killed first.
        settingsRepository.setBoardImage(boardId, token)
        workScheduler.scheduleCoverUpload(boardId, token, previousToken)
        return true
    }

    /** Remove a board's cover locally, in settings, and on Nextcloud. */
    fun removeCover(boardId: Long) {
        val previousToken = settingsRepository.settings.value.boardImages[boardId]
        settingsRepository.setBoardImage(boardId, null)
        BoardCovers.clearCacheFor(context, boardId)
        _readyCovers.update { it - boardId }
        if (previousToken != null) {
            appScope.launch {
                try {
                    val session = authRepository.currentSession() ?: return@launch
                    davClient.deleteFile(session, BoardCovers.remotePath(boardId, previousToken))
                } catch (e: Exception) {
                    Log.w(TAG, "Cover delete failed for board $boardId: ${e.message}")
                }
            }
        }
    }
}
