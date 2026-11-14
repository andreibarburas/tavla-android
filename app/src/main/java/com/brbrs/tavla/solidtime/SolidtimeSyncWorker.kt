package com.brbrs.tavla.solidtime

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Replays start/stop operations that were queued while the device was offline. Enqueued (unique,
 * network-constrained) by the repository whenever an op is queued; retries with backoff until the
 * queue drains.
 */
@HiltWorker
class SolidtimeSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: SolidtimeRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!repository.isConnected) return Result.success()
        val drained = runCatching { repository.syncPending() }.getOrDefault(false)
        return if (drained) Result.success() else Result.retry()
    }
}
