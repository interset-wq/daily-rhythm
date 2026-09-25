package com.intersetwq.dailyrhythm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/** 通知上的"已完成"按钮：记录整批并取消通知（兼容旧的单条 extra）。 */
class TakenActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val batchIds: LongArray = intent.getLongArrayExtra(AlarmScheduler.EXTRA_BATCH)
            ?: intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L).takeIf { it != -1L }
                ?.let { longArrayOf(it) }
            ?: return
        if (batchIds.isEmpty()) return

        val reminders = ReminderStore.loadReminders(context)
        val now = System.currentTimeMillis()
        for (id in batchIds) {
            val reminder = reminders.firstOrNull { it.id == id } ?: continue
            ReminderStore.addLog(context, DoseLog(id, reminder.title, now, true))
        }
        // 一批共用触发条的通知 id
        NotificationManagerCompat.from(context)
            .cancel((batchIds.first() % Int.MAX_VALUE).toInt())
    }
}
