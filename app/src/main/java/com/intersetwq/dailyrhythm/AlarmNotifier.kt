package com.intersetwq.dailyrhythm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** 普通强度提醒：通知栏通知（点“已服用”直接记录，点通知体进入主界面）。 */
object AlarmNotifier {

    const val CHANNEL_REMINDER = "reminder"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_REMINDER) == null) {
            val ch = NotificationChannel(
                CHANNEL_REMINDER, "提醒", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                setSound(null, null)
            }
            nm.createNotificationChannel(ch)
        }
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(ctx: Context, reminder: Reminder) = show(ctx, listOf(reminder))

    /** 同刻合并通知：一批到点的提醒合成一条，一次"已完成"全部打卡。 */
    fun show(ctx: Context, batch: List<Reminder>) {
        ensureChannel(ctx)
        if (!canNotify(ctx)) return
        if (batch.isEmpty()) return

        val notifId = (batch.first().id % Int.MAX_VALUE).toInt()

        // 打开主界面
        val openApp = PendingIntent.getActivity(
            ctx, notifId,
            Intent(ctx, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // "已完成"直接记录整批
        val takenPi = PendingIntent.getBroadcast(
            ctx, notifId,
            Intent(ctx, TakenActionReceiver::class.java)
                .setAction("com.intersetwq.dailyrhythm.TAKEN")
                .putExtra(AlarmScheduler.EXTRA_BATCH, batch.map { it.id }.toLongArray()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = batch.joinToString("、") { r ->
            buildString {
                append(r.title)
                if (r.note.isNotBlank()) append("（").append(r.note).append("）")
            }
        }

        val notif = NotificationCompat.Builder(ctx, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(if (batch.size == 1) "提醒时间到" else "提醒时间到（${batch.size} 项）")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .addAction(R.drawable.ic_stat_reminder, "已完成", takenPi)
            .build()

        NotificationManagerCompat.from(ctx).notify(notifId, notif)
    }
}
