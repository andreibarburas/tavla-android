package com.brbrs.tavla.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.brbrs.tavla.data.DeckRepository
import com.brbrs.tavla.reminders.ReminderScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class RescheduleRemindersWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val deckRepository: DeckRepository,
    private val reminderScheduler: ReminderScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val until = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000
        val cards = deckRepository.observeDueCards(until).first()
        cards.forEach { reminderScheduler.reschedule(it.card) }
        return Result.success()
    }
}
