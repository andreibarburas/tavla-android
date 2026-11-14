package com.brbrs.tavla.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.brbrs.tavla.auth.AuthRepository
import com.brbrs.tavla.data.DeckRepository
import com.brbrs.tavla.reminders.ReminderScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * #03: refreshes every board in the background on the interval chosen in Settings, so opening
 * Tavla never shows stale data. Also reschedules due-date reminders (#04) afterwards, so a due
 * date changed on another device or the web still gets a local notification here.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val authRepository: AuthRepository,
    private val deckRepository: DeckRepository,
    private val reminderScheduler: ReminderScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (authRepository.currentSession() == null) return Result.success()
        return runCatching {
            deckRepository.refreshAll()
            rescheduleUpcoming()
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    private suspend fun rescheduleUpcoming() {
        val until = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000
        val due = deckRepository.observeDueCards(until)
        // observeDueCards is a Flow backed by Room; a worker just needs one current snapshot.
        val cards = due.first()
        cards.forEach { reminderScheduler.reschedule(it.card) }
    }
}
