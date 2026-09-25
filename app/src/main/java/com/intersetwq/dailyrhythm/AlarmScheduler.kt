package com.intersetwq.dailyrhythm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 精确闹钟调度：为所有启用的提醒安排“下一次触发”。
 * 每条 PendingIntent 用 reminderId 区分；触发后由 Receiver 重新安排再下一次。
 */
object AlarmScheduler {

    const val EXTRA_REMINDER_ID = "reminder_id"
    private const val REQ_BASE = 100000

    fun canScheduleExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    /** 重新计算并为每条提醒设置下一次闹钟（覆盖旧的同 id 闹钟）。 */
    fun rescheduleAll(ctx: Context) {
        val reminders = ReminderStore.loadReminders(ctx)
        val now = LocalDateTime.now()
        for (r in reminders) {
            cancel(ctx, r.id)
            if (!r.enabled) continue
            val next = OccurrenceCalculator.nextAfter(r, now) ?: continue
            setExact(ctx, r.id, next, r.strength)
        }
    }

    /** 只为单条提醒安排下一次（触发后滚动调用）。 */
    fun scheduleNextFor(ctx: Context, reminderId: Long) {
        val r = ReminderStore.loadReminders(ctx).firstOrNull { it.id == reminderId } ?: return
        cancel(ctx, reminderId)
        if (!r.enabled) return
        val next = OccurrenceCalculator.nextAfter(r, LocalDateTime.now()) ?: return
        setExact(ctx, reminderId, next, r.strength)
    }

    fun cancel(ctx: Context, reminderId: Long) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(ctx, reminderId))
    }

    private fun setExact(ctx: Context, id: Long, at: LocalDateTime, strength: AlarmStrength) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = pendingIntent(ctx, id)
        val canExact = canScheduleExact(ctx)
        if (strength == AlarmStrength.FULL_ALARM) {
            if (canExact) am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, pi), pi)
            else am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 60_000L, pi)
        } else {
            if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            else am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 60_000L, pi)
        }
    }

    private fun pendingIntent(ctx: Context, id: Long): PendingIntent {
        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
            action = "com.intersetwq.dailyrhythm.ALARM_FIRE"
            putExtra(EXTRA_REMINDER_ID, id)
        }
        return PendingIntent.getBroadcast(
            ctx, (REQ_BASE + id).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
