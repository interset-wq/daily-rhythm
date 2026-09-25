package com.intersetwq.dailyrhythm

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 强提醒全屏页：锁屏可见、亮屏、循环铃声+震动，必须点“已服用 / 跳过 / 稍后提醒”才停。
 */
class AlarmActivity : AppCompatActivity() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var batchIds: List<Long> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SystemBarsHelper.apply(this, findViewById(R.id.alarmRoot))
        setContentView(R.layout.activity_alarm)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        batchIds = intent.getLongArrayExtra(EXTRA_IDS)?.toList()
            ?: intent.getLongExtra(EXTRA_ID, -1L).takeIf { it != -1L }?.let { listOf(it) }
            ?: emptyList()
        val reminders = ReminderStore.loadReminders(this)
            .filter { it.id in batchIds }
        findViewById<TextView>(R.id.tvTitle).text =
            if (reminders.size == 1) reminders.firstOrNull()?.title ?: "提醒"
            else "本次 ${reminders.size} 项：" + reminders.joinToString("、") { it.title }
        findViewById<TextView>(R.id.tvNote).text = reminders
            .mapNotNull { it.note.takeIf { n -> n.isNotBlank() } }
            .joinToString("；")

        startAlert()

        findViewById<Button>(R.id.btnTaken).setOnClickListener { finishWithLog(true) }
        findViewById<Button>(R.id.btnSkip).setOnClickListener { finishWithLog(false) }
        findViewById<Button>(R.id.btnSnooze).setOnClickListener {
            stopAlert()
            SnoozeHelper.snooze(this, batchIds)
            finish()
        }
    }

    private fun startAlert() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            player = MediaPlayer().apply {
                setDataSource(this@AlarmActivity, uri)
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

    private fun stopAlert() {
        player?.run { runCatching { stop(); release() } }
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun finishWithLog(taken: Boolean) {
        stopAlert()
        val reminders = ReminderStore.loadReminders(this)
        val now = System.currentTimeMillis()
        for (id in batchIds) {
            reminders.firstOrNull { it.id == id }?.let {
                ReminderStore.addLog(this, DoseLog(id, it.title, now, taken))
            }
        }
        finish()
    }

    override fun onDestroy() {
        stopAlert()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_IDS = "reminder_ids"

        /** 兼容单条；同刻分组时传多条。 */
        fun launch(ctx: Context, ids: List<Long>) {
            val i = Intent(ctx, AlarmActivity::class.java)
                .putExtra(EXTRA_IDS, ids.toLongArray())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        }
    }
}
