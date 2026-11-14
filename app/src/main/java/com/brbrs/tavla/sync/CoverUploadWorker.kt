package com.brbrs.tavla.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.brbrs.tavla.auth.AuthRepository
import com.brbrs.tavla.settings.BoardCovers
import com.brbrs.tavla.settings.WebDavClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Uploads a board's cover image to the user's own Nextcloud, with WorkManager's retry/backoff
 * surviving app restarts and process death. Previously this upload was a bare coroutine launched
 * in [com.brbrs.tavla.settings.BoardCoverRepository.setCover]: if it failed or the process died
 * before it finished, nothing retried it -- yet the cover's *token* was already saved (locally and
 * synced to Nextcloud in tavla-settings.json), since that write is separate and immediate. On a
 * fresh install, [com.brbrs.tavla.settings.BoardCoverRepository] would then try to download a cover
 * whose bytes had never actually made it to the server, and silently give up -- the cover simply
 * never came back. Routing the upload through a durable worker closes that gap for every cover set
 * from here on.
 */
@HiltWorker
class CoverUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val authRepository: AuthRepository,
    private val davClient: WebDavClient,
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_BOARD_ID = "board_id"
        const val KEY_TOKEN = "token"
        const val KEY_PREVIOUS_TOKEN = "previous_token"
    }

    override suspend fun doWork(): Result {
        val boardId = inputData.getLong(KEY_BOARD_ID, -1L)
        val token = inputData.getString(KEY_TOKEN)
        if (boardId < 0 || token == null) return Result.failure()

        val file = BoardCovers.cacheFile(applicationContext, boardId, token)
        if (!file.exists()) {
            // The cache file is gone (cover removed/replaced again before this ran) -- nothing to upload.
            return Result.success()
        }
        val session = authRepository.currentSession() ?: return Result.retry()

        return runCatching {
            davClient.ensureFolder(session, "Tavla")
            davClient.ensureFolder(session, BoardCovers.REMOTE_FOLDER)
            davClient.putBytes(session, BoardCovers.remotePath(boardId, token), file.readBytes(), "image/jpeg")
            val previousToken = inputData.getString(KEY_PREVIOUS_TOKEN)
            if (previousToken != null && previousToken != token) {
                runCatching { davClient.deleteFile(session, BoardCovers.remotePath(boardId, previousToken)) }
            }
        }.fold(
            onSuccess = { Result.success() },
            onFailure = {
                Log.w("CoverUpload", "Cover upload failed for board $boardId: ${it.message}")
                Result.retry()
            },
        )
    }
}
