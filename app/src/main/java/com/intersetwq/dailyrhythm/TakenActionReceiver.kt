package com.intersetwq.dailyrhythm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/** 通知上的“已服用”按钮：记录并取消通知。 */
class TakenActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L)
        if (id == -1L) return
        val reminder = ReminderStore.loadReminders(context).firstOrNull { it.id == id } ?: return
        ReminderStore.addLog(context, DoseLog(id, reminder.title, System.currentTimeMillis(), true))
        NotificationManagerCompat.from(context)
            .cancel((id % Int.MAX_VALUE).toInt())
    }
}
