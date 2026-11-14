package com.brbrs.tavla.solidtime.timer

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.brbrs.tavla.MainActivity
import com.brbrs.tavla.R
import com.brbrs.tavla.reminders.NotificationHelper
import com.brbrs.tavla.solidtime.SolidtimeRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A foreground service that shows the running-timer notification. Using a foreground service (not
 * a bare notification) keeps the elapsed time ticking and the Stop action working while Tavla is
 * in the background. The notification's elapsed time is rendered by the system via a chronometer
 * anchored to the timer's start, so the service doesn't have to wake up every second to update it.
 *
 * The service is the repository's mirror: it watches [SolidtimeRepository.runningTimer] and stops
 * itself the moment that becomes null (timer stopped, here or on the web).
 */
@AndroidEntryPoint
class TimerService : Service() {

    @Inject lateinit var repository: SolidtimeRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Post an initial notification immediately so startForeground's 5s deadline is always met,
        // even before the flow emits.
        startForegroundCompat(buildNotification(repository.runningTimer.value))
        scope.launch {
            repository.runningTimer.collect { timer ->
                if (timer == null) {
                    stopForegroundCompat()
                    stopSelf()
                } else {
                    notificationManagerCompat().notify(NOTIFICATION_ID, buildNotification(timer))
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun notificationManagerCompat() = androidx.core.app.NotificationManagerCompat.from(this)

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
    }

    private fun buildNotification(timer: RunningTimer?): Notification {
        val title = timer?.cardTitle?.takeIf { it.isNotBlank() }
            ?: timer?.description?.takeIf { it.isNotBlank() }
            ?: "Tracking time"
        val sub = timer?.projectName?.let { "Solidtime · $it" } ?: "Solidtime"

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                timer?.cardId?.let { putExtra(MainActivity.EXTRA_OPEN_CARD_ID, it) }
                timer?.boardId?.let { putExtra(MainActivity.EXTRA_OPEN_BOARD_ID, it) }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getBroadcast(
            this, 1,
            Intent(this, TimerActionReceiver::class.java).setAction(TimerActionReceiver.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_TIMER)
            .setSmallIcon(R.drawable.tavla_logo)
            .setContentTitle(title)
            .setContentText(sub)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Stop", stopIntent)
        if (timer != null) {
            builder.setUsesChronometer(true).setWhen(timer.startMs).setShowWhen(true)
        }
        return builder.build()
    }

    companion object {
        const val NOTIFICATION_ID = 7021
    }
}
