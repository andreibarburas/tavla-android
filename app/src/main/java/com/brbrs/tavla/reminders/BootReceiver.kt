package com.brbrs.tavla.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.brbrs.tavla.sync.WorkScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var workScheduler: WorkScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // AlarmManager alarms are wiped on reboot; hand off to a worker rather than doing
        // Room/DataStore I/O directly in a broadcast receiver, which has a very short time budget.
        workScheduler.rescheduleRemindersAfterBoot()
    }
}
