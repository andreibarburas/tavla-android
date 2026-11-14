package com.brbrs.tavla.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.brbrs.tavla.data.Card
import com.brbrs.tavla.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * All reminders are plain [AlarmManager] alarms scheduled on-device -- no push server, nothing
 * leaves the phone. One alarm per (card, offset) pair, e.g. a card with both "1 day" and "1 hour"
 * offsets enabled gets two alarms. Alarms don't survive a reboot, so [BootReceiver] re-schedules
 * everything open from the local cache on startup.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)

    private fun requestCode(cardId: Long, offsetMinutes: Int): Int =
        ((cardId % 100_000) * 2_000 + offsetMinutes).toInt()

    private fun pendingIntent(cardId: Long, boardId: Long, title: String, offsetMinutes: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_CARD_ID, cardId)
            putExtra(ReminderReceiver.EXTRA_BOARD_ID, boardId)
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_OFFSET_MINUTES, offsetMinutes)
        }
        return PendingIntent.getBroadcast(
            context, requestCode(cardId, offsetMinutes), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Cancels any existing alarms for [cardId] then, if reminders are on and the due date is in the future, re-schedules them. */
    fun reschedule(card: Card, boardId: Long = card.boardId) {
        val settings = settingsRepository.settings.value
        // Every offset the person might have used historically needs cancelling, not just the
        // current list, or a removed offset would keep firing for cards scheduled before the change.
        val allPossibleOffsets = (settings.reminderOffsetsMinutes + listOf(15, 60, 180, 1440, 2880)).distinct()
        allPossibleOffsets.forEach { offset -> cancelOne(card.id, boardId, card.title, offset) }

        if (!settings.remindersEnabled || card.archived || card.doneAt != null || card.dueDate == null) return
        val now = System.currentTimeMillis()
        settings.reminderOffsetsMinutes.forEach { offsetMinutes ->
            val triggerAt = card.dueDate - offsetMinutes * 60_000L
            if (triggerAt <= now) return@forEach
            val pending = pendingIntent(card.id, boardId, card.title, offsetMinutes)
            runCatching {
                val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager?.canScheduleExactAlarms() == true
                if (canExact) {
                    alarmManager?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                } else {
                    // No "exact alarm" permission granted -- an inexact alarm still fires, just not to the minute.
                    alarmManager?.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                }
            }
        }
    }

    fun cancel(cardId: Long, boardId: Long) {
        val allPossibleOffsets = (settingsRepository.settings.value.reminderOffsetsMinutes + listOf(15, 60, 180, 1440, 2880)).distinct()
        allPossibleOffsets.forEach { offset -> cancelOne(cardId, boardId, "", offset) }
    }

    private fun cancelOne(cardId: Long, boardId: Long, title: String, offsetMinutes: Int) {
        alarmManager?.cancel(pendingIntent(cardId, boardId, title, offsetMinutes))
    }

    /** True from Android 12 onward when the exact-alarm permission still needs to be granted in system settings. */
    fun needsExactAlarmPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager?.canScheduleExactAlarms() == false
}
