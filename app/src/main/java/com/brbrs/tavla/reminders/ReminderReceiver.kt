package com.brbrs.tavla.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.brbrs.tavla.data.DeckRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var repository: DeckRepository

    companion object {
        const val EXTRA_CARD_ID = "card_id"
        const val EXTRA_BOARD_ID = "board_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_OFFSET_MINUTES = "offset_minutes"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val cardId = intent.getLongExtra(EXTRA_CARD_ID, -1L)
        if (cardId < 0) return
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "A card is due soon" }
        val offset = intent.getIntExtra(EXTRA_OFFSET_MINUTES, 0)
        val body = when {
            offset <= 0 -> "Due now"
            offset < 60 -> "Due in $offset minutes"
            offset < 24 * 60 && offset % 60 == 0 -> "Due in ${offset / 60} hour${if (offset / 60 == 1) "" else "s"}"
            offset % (24 * 60) == 0 -> "Due in ${offset / (24 * 60)} day${if (offset / (24 * 60) == 1) "" else "s"}"
            else -> "Due soon"
        }

        // An AlarmManager alarm is fire-and-forget: it can outlive the card it was scheduled for
        // if the card was later completed, archived or deleted through a path that didn't happen
        // to cancel it (e.g. completing it from the board list, or a deletion that arrived via
        // sync from another device). So re-check the card's live state here, right before
        // notifying, instead of trusting what was baked into the alarm when it was scheduled.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (repository.isCardStillDue(cardId)) {
                    notificationHelper.showReminder(cardId, title, body)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
