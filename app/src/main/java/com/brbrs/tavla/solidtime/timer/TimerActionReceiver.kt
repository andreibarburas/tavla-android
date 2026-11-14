package com.brbrs.tavla.solidtime.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.brbrs.tavla.di.ApplicationScope
import com.brbrs.tavla.solidtime.SolidtimeRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Handles the "Stop" action on the running-timer notification. */
@AndroidEntryPoint
class TimerActionReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: SolidtimeRepository
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STOP) return
        val pending = goAsync()
        appScope.launch {
            try {
                repository.stopActiveTimer()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_STOP = "com.brbrs.tavla.solidtime.STOP_TIMER"
    }
}
