package com.intersetwq.dailyrhythm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * 普通强度提醒：通知栏通知（点"已完成"直接记录整批，点通知体进入主界面）。
 *
 * 渠道说明：旧版曾创建无声渠道 "reminder"（且被系统/用户锁定重要性），
 * 通知渠道创建后除 ID 外一切属性不可改 —— 这里换用新 ID 重建有声渠道，
 * 并删除旧渠道；声音开关用两个不同渠道承载。
 */
object AlarmNotifier {

    private const val OLD_CHANNEL = "reminder"
    const val CHANNEL_SOUND = "reminder_v2"
    const val CHANNEL_MUTE = "reminder_mute"

    // 注意：Android 对"删除后同 ID 重建"的渠道会保留旧设置，
    // 渠道被系统降级后必须换新 ID 才能恢复 HIGH —— 版本号随降级递增。
    const val CHANNEL_ALARM = "alarm_v2"
    const val ALARM_NOTIF_ID = 3001

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** 创建/修正通知渠道：有声（HIGH，横幅+铃声+震动）与无声两个。 */
    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // 删除旧版无声渠道，摆脱被锁定的降级配置
        nm.deleteNotificationChannel(OLD_CHANNEL)

        // 荣耀等 ROM 会在异常/更新后自动把渠道降级（4→3），
        // 低重要性会同时压制横幅与全屏意图 —— 发现阶段低于 HIGH 就删掉重建。
        fun ensureHigh(id: String, build: () -> NotificationChannel) {
            nm.getNotificationChannel(id)?.let {
                if (it.importance < NotificationManager.IMPORTANCE_HIGH) {
                    nm.deleteNotificationChannel(id)
                }
            }
            nm.createNotificationChannel(build())
        }

        val soundAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        ensureHigh(CHANNEL_SOUND) {
            NotificationChannel(
                CHANNEL_SOUND, "提醒（响铃）", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(defaultSound, soundAttrs)
                enableVibration(true)
                enableLights(true)
            }
        }

        ensureHigh(CHANNEL_MUTE) {
            NotificationChannel(
                CHANNEL_MUTE, "提醒（静音）", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                enableVibration(true)
            }
        }

        // 强提醒全屏闹钟渠道：闹钟铃声 + USAGE_ALARM（独立于通知音量，勿扰默认放行闹钟）
        ensureHigh(CHANNEL_ALARM) {
            NotificationChannel(
                CHANNEL_ALARM, "强提醒（全屏闹钟）", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
            }
        }
    }

    fun show(ctx: Context, reminder: Reminder) = show(ctx, listOf(reminder))

    /** 同刻合并通知：一批到点的提醒合成一条，一次"已完成"全部打卡。 */
    fun show(ctx: Context, batch: List<Reminder>) {
        ensureChannels(ctx)
        if (!canNotify(ctx)) return
        if (batch.isEmpty()) return

        // 声音开关决定走哪个渠道（渠道属性不可改，用渠道切换承载设置）
        val channel = if (SettingsStore.soundEnabled(ctx)) CHANNEL_SOUND else CHANNEL_MUTE
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

        val notif = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(if (batch.size == 1) "提醒时间到" else "提醒时间到（${batch.size} 项）")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .addAction(R.drawable.ic_stat_reminder, "已完成", takenPi)
            .build()

        NotificationManagerCompat.from(ctx).notify(notifId, notif)
    }

    /**
     * 强提醒：全屏意图通知（系统时钟 app 的做法）。
     * 锁屏/熄屏时直接拉起全屏闹钟页；解锁状态显示为横幅。
     * 不直接 startActivity —— 荣耀等 ROM 会拦截后台启动 Activity，全屏意图是系统放行路径。
     */
    fun showFullScreen(ctx: Context, batch: List<Reminder>) {
        ensureChannels(ctx)
        if (!canNotify(ctx)) return
        if (batch.isEmpty()) return

        val pi = PendingIntent.getActivity(
            ctx, ALARM_NOTIF_ID,
            Intent(ctx, AlarmActivity::class.java)
                .putExtra(AlarmActivity.EXTRA_IDS, batch.map { it.id }.toLongArray())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = batch.joinToString("、") { it.title }
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(if (batch.size == 1) "闹钟" else "闹钟（${batch.size} 项）")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pi, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        NotificationManagerCompat.from(ctx).notify(ALARM_NOTIF_ID, notif)
    }

    /** 闹钟处理完成（已完成/跳过/稍后）后撤下全屏意图通知。 */
    fun cancelAlarmNotification(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(ALARM_NOTIF_ID)
    }
}
