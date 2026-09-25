package com.intersetwq.dailyrhythm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDateTime
import java.time.ZoneId

/** 稍后提醒：10 分钟后以同样强度再触发一次。 */
object SnoozeHelper {
    private const val MINUTES = 10L

    fun snooze(ctx: Context, reminderId: Long) {
        val reminder = ReminderStore.loadReminders(ctx).firstOrNull { it.id == reminderId } ?: return
        val at = LocalDateTime.now().plusMinutes(MINUTES)
        val triggerAt = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val intent = Intent(ctx, AlarmReceiver::class.java)
            .setAction("com.intersetwq.dailyrhythm.ALARM_FIRE")
            .putExtra(AlarmScheduler.EXTRA_REMINDER_ID, reminderId)
        val pi = PendingIntent.getBroadcast(
            ctx, (700000 + reminderId).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        else am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 60_000L, pi)
    }
}
