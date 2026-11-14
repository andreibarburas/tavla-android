package com.brbrs.tavla.contactlinks

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Pushes the local card <-> contact link table to the `Tavla/contact-links.json` sidecar on
 * Nextcloud, retried by WorkManager with backoff until it lands. Previously
 * [ContactLinksSync.notifyChanged] pushed via a bare, cancellable coroutine delay -- a change
 * that hadn't been written yet when the process died (e.g. right before an uninstall) was lost
 * for good, leaving a reinstall with nothing to pull back for that card's linked contacts.
 */
@HiltWorker
class ContactLinksPushWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val linksSync: ContactLinksSync,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (linksSync.pushDurable()) Result.success() else Result.retry()
}

/**
 * Pulls `Tavla/contact-links.json` from Nextcloud and merges it into the local Room database,
 * retried by WorkManager with backoff until it succeeds. This is what restores a card's linked
 * Vinci contacts after a reinstall.
 */
@HiltWorker
class ContactLinksPullWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val linksSync: ContactLinksSync,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (linksSync.pullDurable()) Result.success() else Result.retry()
}
