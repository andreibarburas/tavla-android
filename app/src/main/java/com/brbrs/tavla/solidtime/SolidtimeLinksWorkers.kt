package com.brbrs.tavla.solidtime

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Pushes the local Solidtime link tables (board links, card-project overrides, tracked entries,
 * generated tasks) to the `Tavla/solidtime-links.json` sidecar on Nextcloud, retried by
 * WorkManager with backoff until it lands. Previously [SolidtimeLinksSync.notifyChanged] pushed
 * via a bare, cancellable coroutine delay -- if the process died before the debounce elapsed
 * (e.g. right before an uninstall), the change was never written to the server, so a reinstall
 * had nothing to pull back for that card-project link or entry.
 */
@HiltWorker
class SolidtimeLinksPushWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val linksSync: SolidtimeLinksSync,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (linksSync.pushDurable()) Result.success() else Result.retry()
}

/**
 * Pulls `Tavla/solidtime-links.json` from Nextcloud and merges it into the local Room database,
 * retried by WorkManager with backoff until it succeeds. This is what restores board links,
 * per-card Solidtime project overrides, and tracked time entries after a reinstall -- previously
 * a single best-effort attempt that silently gave up on any network hiccup, with nothing to
 * retry it.
 */
@HiltWorker
class SolidtimeLinksPullWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val linksSync: SolidtimeLinksSync,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (linksSync.pullDurable()) Result.success() else Result.retry()
}
