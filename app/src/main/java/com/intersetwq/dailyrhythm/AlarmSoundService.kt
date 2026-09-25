package com.intersetwq.dailyrhythm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

/**
 * 强提醒响铃服务：闹钟到点由 AlarmReceiver 直接启动——触发即响铃+震动，
 * 不依赖全屏意图是否被 ROM 放行（系统时钟 app 的做法）。
 * 全屏页只是 UI；用户处理（已完成/跳过/稍后）后由 AlarmActivity 调 stop() 停止。
 */
class AlarmSoundService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ids = intent?.getLongArrayExtra(AlarmActivity.EXTRA_IDS)?.toList().orEmpty()
        startForeground(NOTIF_ID, buildNotification(ids))
        startAlert()
        return START_NOT_STICKY
    }

    private fun buildNotification(ids: List<Long>): Notification {
        // 用静音渠道做前台通知，铃声由本服务播放，避免双重响铃；
        // 点通知体进入全屏闹钟页。
        val openAlarm = PendingIntent.getActivity(
            this, 4002,
            Intent(this, AlarmActivity::class.java)
                .putExtra(AlarmActivity.EXTRA_IDS, ids.toLongArray())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, AlarmNotifier.CHANNEL_MUTE)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle("闹钟响铃中")
            .setContentText("点击处理本次提醒")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(openAlarm, true)
            .setOngoing(true)
            .build()
    }

    private fun startAlert() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            player = MediaPlayer().apply {
                setDataSource(this@AlarmSoundService, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        }
        val vib = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(VibratorManager::class.java)).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        vibrator = vib
        val pattern = longArrayOf(0, 600, 400)
        if (Build.VERSION.SDK_INT >= 26) {
            vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(pattern, 0)
        }
    }

    override fun onDestroy() {
        player?.run { runCatching { stop(); release() } }
        player = null
        vibrator?.cancel()
        vibrator = null
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 4001

        fun start(ctx: Context, ids: List<Long>) {
            val i = Intent(ctx, AlarmSoundService::class.java)
                .putExtra(AlarmActivity.EXTRA_IDS, ids.toLongArray())
            if (Build.VERSION.SDK_INT >= 26) {
                ctx.startForegroundService(i)
            } else {
                ctx.startService(i)
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, AlarmSoundService::class.java))
        }
    }
}
