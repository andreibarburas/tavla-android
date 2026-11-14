package com.brbrs.tavla.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.brbrs.tavla.MainActivity
import com.brbrs.tavla.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_REMINDERS = "tavla_reminders"
        const val CHANNEL_TIMER = "tavla_timer" // reserved for the Solidtime integration's running-timer notification
    }

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Due date reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A reminder before a card's due date arrives"
            },
        )
        // Low importance: the running-timer notification is ongoing and silent, not an alert.
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TIMER, "Running timer", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows the Solidtime timer while it is running"
                setShowBadge(false)
            },
        )
    }

    /** True once the person has granted POST_NOTIFICATIONS (always true before Android 13). */
    fun canNotify(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun showReminder(cardId: Long, title: String, body: String) {
        if (!canNotify()) return
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, cardId.toInt(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.tavla_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(cardId.toInt(), notification)
        }
    }
}
