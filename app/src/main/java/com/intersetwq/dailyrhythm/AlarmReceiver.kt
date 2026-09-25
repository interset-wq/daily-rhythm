package com.intersetwq.dailyrhythm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 闹钟到点：FULL_ALARM 弹全屏页面，NOTIFICATION 发通知；然后滚动安排下一次。
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L)
        if (id == -1L) return
        val reminder = ReminderStore.loadReminders(context).firstOrNull { it.id == id } ?: return

        if (reminder.strength == AlarmStrength.FULL_ALARM) {
            AlarmActivity.launch(context, id)
        } else {
            AlarmNotifier.show(context, reminder)
        }
        // 滚动安排下一次（一次性提醒内部会自然返回 null，不再排）
        AlarmScheduler.scheduleNextFor(context, id)
    }
}
