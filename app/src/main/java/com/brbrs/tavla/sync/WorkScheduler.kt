package com.brbrs.tavla.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val SYNC_WORK_NAME = "tavla_periodic_sync"
        private const val RESCHEDULE_WORK_NAME = "tavla_reschedule_reminders"
        private const val EDIT_REPLAY_WORK_NAME = "tavla_edit_replay"
        private const val COVER_UPLOAD_WORK_PREFIX = "tavla_cover_upload_"
        private const val SOLIDTIME_LINKS_PUSH_WORK_NAME = "tavla_solidtime_links_push"
        private const val SOLIDTIME_LINKS_PULL_WORK_NAME = "tavla_solidtime_links_pull"
        private const val CONTACT_LINKS_PUSH_WORK_NAME = "tavla_contact_links_push"
        private const val CONTACT_LINKS_PULL_WORK_NAME = "tavla_contact_links_pull"

        /** WorkManager refuses periodic intervals under 15 minutes; Settings only offers values at or above this. */
        const val MIN_SYNC_INTERVAL_MINUTES = 15
    }

    /** (Re)schedules background sync at [intervalMinutes]. Safe to call every time the setting changes. */
    fun scheduleSync(intervalMinutes: Int) {
        val interval = intervalMinutes.coerceAtLeast(MIN_SYNC_INTERVAL_MINUTES)
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(interval.toLong(), TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(SYNC_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancelSync() {
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
    }

    /** Enqueues a one-shot worker to replay offline edits as soon as the device has a network connection. */
    fun scheduleEditReplay() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<EditReplayWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(EDIT_REPLAY_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * Enqueues a durable upload of a board's cover image, retried by WorkManager (with backoff)
     * until it succeeds -- rather than a fire-and-forget coroutine that silently loses the upload
     * if it fails or the process dies first. Unique per board, REPLACE so setting a new cover right
     * after another supersedes the still-pending upload instead of racing it.
     */
    fun scheduleCoverUpload(boardId: Long, token: String, previousToken: String?) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val data = androidx.work.Data.Builder()
            .putLong(CoverUploadWorker.KEY_BOARD_ID, boardId)
            .putString(CoverUploadWorker.KEY_TOKEN, token)
            .apply { if (previousToken != null) putString(CoverUploadWorker.KEY_PREVIOUS_TOKEN, previousToken) }
            .build()
        val request = OneTimeWorkRequestBuilder<CoverUploadWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("$COVER_UPLOAD_WORK_PREFIX$boardId", ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * Enqueues a durable, debounced push of the Solidtime link tables (board links, per-card
     * project overrides, tracked entries, generated tasks) to Nextcloud. REPLACE mirrors the
     * debounce this replaces: a rapid second change cancels the still-pending push and schedules
     * a fresh one, which is safe here since the worker reads Room live rather than from a
     * snapshot taken at schedule time.
     */
    fun scheduleSolidtimeLinksPush() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<com.brbrs.tavla.solidtime.SolidtimeLinksPushWorker>()
            .setConstraints(constraints)
            .setInitialDelay(2, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(SOLIDTIME_LINKS_PUSH_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * Enqueues a durable pull of the Solidtime link tables from Nextcloud, retried with backoff
     * until it succeeds -- this is what restores board links, per-card project overrides, and
     * tracked entries after a reinstall. KEEP since this is called from several places around
     * app/session start in close succession; no need to restart an already-pending attempt.
     */
    fun scheduleSolidtimeLinksPull() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<com.brbrs.tavla.solidtime.SolidtimeLinksPullWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(SOLIDTIME_LINKS_PULL_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /** Enqueues a durable, debounced push of card <-> Vinci-contact links to Nextcloud. See [scheduleSolidtimeLinksPush]. */
    fun scheduleContactLinksPush() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<com.brbrs.tavla.contactlinks.ContactLinksPushWorker>()
            .setConstraints(constraints)
            .setInitialDelay(2, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(CONTACT_LINKS_PUSH_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** Enqueues a durable pull of card <-> Vinci-contact links from Nextcloud. See [scheduleSolidtimeLinksPull]. */
    fun scheduleContactLinksPull() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<com.brbrs.tavla.contactlinks.ContactLinksPullWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(CONTACT_LINKS_PULL_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /** Called by [com.brbrs.tavla.reminders.BootReceiver] since AlarmManager alarms don't survive a reboot. */
    fun rescheduleRemindersAfterBoot() {
        val request = OneTimeWorkRequestBuilder<RescheduleRemindersWorker>().build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(RESCHEDULE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
