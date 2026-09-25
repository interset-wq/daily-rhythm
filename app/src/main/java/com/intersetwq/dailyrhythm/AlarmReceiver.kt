package com.intersetwq.dailyrhythm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 闹钟到点：先做同刻分组（把同一时刻到点的提醒合并为一批），
 * FULL_ALARM 弹全屏页列出整批，NOTIFICATION 合并为一条通知；然后滚动安排下一次。
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 单条触发用 EXTRA_REMINDER_ID；整批稍后触发（SnoozeHelper）用 EXTRA_BATCH
        val batchExtra = intent.getLongArrayExtra(AlarmScheduler.EXTRA_BATCH)?.toList().orEmpty()
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L)
            .takeIf { it != -1L }
            ?: batchExtra.firstOrNull()
            ?: return
        val store = ReminderStore.loadReminders(context)
        val reminder = store.firstOrNull { it.id == id } ?: return

        // 同刻分组：A/B 药同一时间到点时合并呈现，一次操作全部处理。
        // 稍后触发的批次直接沿用，不重复分组。
        val batch = if (batchExtra.isNotEmpty()) {
            store.filter { it.id in batchExtra && it.enabled }
        } else {
            AlarmScheduler.collectDueBatch(context, id)
        }
        if (batch.isEmpty()) return

        if (batch.any { it.strength == AlarmStrength.FULL_ALARM }) {
            AlarmActivity.launch(context, batch.map { it.id })
        } else {
            AlarmNotifier.show(context, batch)
        }
        // 触发过的这条滚动安排下一次（同伴的已在 collectDueBatch 内滚动）
        AlarmScheduler.scheduleNextFor(context, id)
    }
}
