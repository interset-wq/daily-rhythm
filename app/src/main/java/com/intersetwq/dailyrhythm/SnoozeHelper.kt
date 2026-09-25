package com.intersetwq.dailyrhythm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDateTime
import java.time.ZoneId

/** 稍后提醒：10 分钟后以同样强度再触发一次（支持整批同刻提醒）。 */
object SnoozeHelper {
    private const val MINUTES = 10L

    /** 兼容单条 */
    fun snooze(ctx: Context, reminderId: Long) = snooze(ctx, listOf(reminderId))

    /** 整批稍后：一次闹钟同时带起所有被合并的提醒。 */
    fun snooze(ctx: Context, reminderIds: List<Long>) {
        if (reminderIds.isEmpty()) return
        val at = LocalDateTime.now().plusMinutes(MINUTES)
        val triggerAt = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val intent = Intent(ctx, AlarmReceiver::class.java)
            .setAction("com.intersetwq.dailyrhythm.ALARM_FIRE")
            .putExtra(AlarmScheduler.EXTRA_BATCH, reminderIds.toLongArray())
        val pi = PendingIntent.getBroadcast(
            ctx, (700000 + reminderIds.first()).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        else am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 60_000L, pi)
    }
}
