package com.brbrs.tavla.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.brbrs.tavla.auth.AuthRepository
import com.brbrs.tavla.data.EditQueueManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Replays pending offline edits when connectivity returns. Scheduled as a one-time unique
 * work request with a CONNECTED network constraint so it runs as soon as the device is online.
 */
@HiltWorker
class EditReplayWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val authRepository: AuthRepository,
    private val editQueue: EditQueueManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (authRepository.currentSession() == null) return Result.success()
        return runCatching {
            val replayed = editQueue.replayAll()
            Log.i("EditReplay", "Replayed $replayed pending edit(s)")
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }
}
