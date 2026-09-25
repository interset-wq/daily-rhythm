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
    const val EXTRA_BATCH = "reminder_batch"
    const val EXTRA_OCC = "occ_millis"
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

    /**
     * 同刻分组：一条闹钟触发时，收集“此刻同样到点”的其他提醒组成一批。
     * 到点判定：该提醒的下一次触发时间落在 [now-容差, now+容差] 内。
     * 被并入批次的同伴闹钟取消并滚动到下一次，避免各自再单独响一次。
     * 同一时刻两条闹钟都会送达（竞态）：用“已处理发生点”标记去重，
     * 后到的那条直接被 onReceive 丢弃，保证整批只呈现一次。
     */
    fun collectDueBatch(ctx: Context, triggeredId: Long): List<Reminder> {
        val now = LocalDateTime.now()
        val tolerance = java.time.Duration.ofMinutes(1)
        val all = ReminderStore.loadReminders(ctx)
        val triggered = all.firstOrNull { it.id == triggeredId && it.enabled } ?: return emptyList()
        val batch = mutableListOf(triggered)
        val tOcc = OccurrenceCalculator.nextAfter(triggered, now.minusSeconds(30)) ?: now
        markOccurrenceHandled(ctx, triggered.id, tOcc)

        for (r in all) {
            if (!r.enabled || r.id == triggeredId) continue
            val next = OccurrenceCalculator.nextAfter(r, now.minusSeconds(30)) ?: continue
            val due = !next.isAfter(now.plus(tolerance)) && !next.isBefore(now.minus(tolerance))
            if (due) {
                batch.add(r)
                markOccurrenceHandled(ctx, r.id, next)
                // 同伴已并入本批：取消其闹钟并滚动到下一次
                cancel(ctx, r.id)
                val later = OccurrenceCalculator.nextAfter(r, now)
                if (later != null) setExact(ctx, r.id, later, r.strength)
            }
        }
        return batch
    }

    // ---- 发生点去重标记（防同刻双闹钟竞态重复呈现）----

    private fun handledPrefs(ctx: Context) =
        ctx.getSharedPreferences("handled_occ", Context.MODE_PRIVATE)

    fun isOccurrenceHandled(ctx: Context, reminderId: Long, occMillis: Long): Boolean =
        handledPrefs(ctx).getLong("occ_$reminderId", -1L) == occMillis

    private fun markOccurrenceHandled(ctx: Context, reminderId: Long, occ: LocalDateTime) {
        val millis = occ.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        handledPrefs(ctx).edit().putLong("occ_$reminderId", millis).apply()
    }

    fun cancel(ctx: Context, reminderId: Long) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(ctx, reminderId))
    }

    private fun setExact(ctx: Context, id: Long, at: LocalDateTime, strength: AlarmStrength) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = pendingIntent(ctx, id, triggerAt)
        val canExact = canScheduleExact(ctx)
        if (strength == AlarmStrength.FULL_ALARM) {
            if (canExact) am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, pi), pi)
            else am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 60_000L, pi)
        } else {
            if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            else am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 60_000L, pi)
        }
    }

    private fun pendingIntent(ctx: Context, id: Long, occMillis: Long = -1L): PendingIntent {
        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
            action = "com.intersetwq.dailyrhythm.ALARM_FIRE"
            putExtra(EXTRA_REMINDER_ID, id)
            putExtra(EXTRA_OCC, occMillis)
        }
        return PendingIntent.getBroadcast(
            ctx, (REQ_BASE + id).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
