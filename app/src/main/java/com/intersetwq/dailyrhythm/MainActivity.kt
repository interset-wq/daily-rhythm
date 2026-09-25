package com.intersetwq.dailyrhythm

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: ReminderAdapter
    private lateinit var tvEmpty: TextView
    private lateinit var pageReminders: View
    private lateinit var pageStats: View
    private lateinit var pageSettings: View
    private lateinit var pageTimeline: View
    private lateinit var tvTitleBar: TextView
    private lateinit var fab: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // 品牌蓝 header 垫在状态栏后面（Android 15 edge-to-edge 兼容）
        SystemBarsHelper.applyWithHeader(this, findViewById(R.id.tvTitleBar))

        tvEmpty = findViewById(R.id.tvEmpty)
        pageReminders = findViewById(R.id.pageReminders)
        pageStats = findViewById(R.id.pageStats)
        pageSettings = findViewById(R.id.pageSettings)
        pageTimeline = findViewById(R.id.pageTimeline)
        tvTitleBar = findViewById(R.id.tvTitleBar)
        fab = findViewById(R.id.fab)

        adapter = ReminderAdapter(
            onToggle = { r -> toggle(r) },
            onEdit = { r -> startActivity(Intent(this, EditReminderActivity::class.java).putExtra("id", r.id)) },
            onDelete = { r -> confirmDelete(r) },
            onPhoto = { r -> PhotoViewer.show(this, r.photoName) }
        )
        val rv = findViewById<RecyclerView>(R.id.rvReminders)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        fab.setOnClickListener {
            startActivity(Intent(this, EditReminderActivity::class.java))
        }

        setupSettingsPage()

        // 底部 Tab：屏幕按钮切换页面，不依赖手势
        findViewById<BottomNavigationView>(R.id.bottomNav).setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_reminders -> {
                    showPage("reminders")
                    true
                }
                R.id.nav_stats -> {
                    showPage("stats")
                    refreshStats()
                    true
                }
                R.id.nav_timeline -> {
                    showPage("timeline")
                    refreshTimeline()
                    true
                }
                R.id.nav_settings -> {
                    showPage("settings")
                    true
                }
                else -> false
            }
        }

        requestNeededPermissions()
    }

    private fun showPage(page: String) {
        val reminders = page == "reminders"
        val stats = page == "stats"
        pageReminders.visibility = if (reminders) View.VISIBLE else View.GONE
        pageStats.visibility = if (stats) View.VISIBLE else View.GONE
        pageTimeline.visibility = if (page == "timeline") View.VISIBLE else View.GONE
        pageSettings.visibility = if (page == "settings") View.VISIBLE else View.GONE
        fab.visibility = if (reminders) View.VISIBLE else View.GONE
        tvTitleBar.text = when (page) {
            "stats" -> getString(R.string.stats)
            "timeline" -> "时间线"
            "settings" -> "设置"
            else -> getString(R.string.app_name)
        }
    }

    /** 时间线：未来 7 天的触发计划 */
    private fun refreshTimeline() {
        val now = java.time.LocalDateTime.now()
        val items = OccurrenceCalculator.upcoming(
            ReminderStore.loadReminders(this), now, now.plusDays(7)
        )
        val rv = findViewById<RecyclerView>(R.id.rvTimeline)
        if (rv.adapter == null) {
            rv.layoutManager = LinearLayoutManager(this)
            rv.adapter = TimelineAdapter()
        }
        (rv.adapter as TimelineAdapter).submit(items)
        findViewById<View>(R.id.tvTimelineEmpty).visibility =
            if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    /** 设置页：读写全局默认值 */
    private fun setupSettingsPage() {
        val swDefaultAlarm = findViewById<Switch>(R.id.swDefaultAlarm)
        val swSound = findViewById<Switch>(R.id.swSound)
        val etSnooze = findViewById<EditText>(R.id.etSnooze)
        val prefs = SettingsStore.defaults(this)

        swDefaultAlarm.isChecked = SettingsStore.defaultFullAlarm(this)
        swSound.isChecked = SettingsStore.soundEnabled(this)
        etSnooze.setText(SettingsStore.snoozeMinutes(this).toString())

        swDefaultAlarm.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(SettingsStore.KEY_DEFAULT_FULL_ALARM, checked).apply()
        }
        swSound.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(SettingsStore.KEY_SOUND_ENABLED, checked).apply()
        }
        etSnooze.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val v = etSnooze.text.toString().toIntOrNull()?.coerceIn(1, 120) ?: 10
                etSnooze.setText(v.toString())
                prefs.edit().putInt(SettingsStore.KEY_SNOOZE_MINUTES, v).apply()
            }
        }

        // 版本号
        runCatching {
            val ver = packageManager.getPackageInfo(packageName, 0).versionName
            findViewById<TextView>(R.id.tvVersion).text = "版本 $ver"
        }
    }

    override fun onResume() {
        super.onResume()
        // 前台自愈：荣耀等 ROM 的深度清理等同 force-stop，会静默清除已注册闹钟
        // 且不发 BOOT 广播；回到前台时重排一次，成本极低（重复 set 覆盖旧闹钟）。
        AlarmScheduler.rescheduleAll(this)
        refresh()
    }

    private fun refresh() {
        val list = ReminderStore.loadReminders(this).sortedBy { it.id }
        adapter.submit(list)
        tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    /** 统计逻辑由原 StatsActivity 迁入 */
    private fun refreshStats() {
        val logs = ReminderStore.loadLogs(this)
        val reminders = ReminderStore.loadReminders(this)
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()

        var expected = 0
        var taken = 0
        for (r in reminders) {
            for (i in 0..6) {
                val d = today.minusDays(i.toLong())
                val count = countOccurrencesOn(r, d)
                if (count <= 0) continue
                expected += count
                val dayStart = d.atStartOfDay(zone).toInstant().toEpochMilli()
                val dayEnd = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                taken += logs.count {
                    it.reminderId == r.id && it.taken && it.time >= dayStart && it.time < dayEnd
                }.coerceAtMost(count)
            }
        }

        val rate = if (expected == 0) 0 else (taken * 100 / expected)
        findViewById<TextView>(R.id.tvRate).text = "$rate%"
        findViewById<TextView>(R.id.tvDetail).text = "近 7 天：应提醒 $expected 次，已执行 $taken 次"

        val recent = logs.sortedByDescending { it.time }.take(20)
        findViewById<TextView>(R.id.tvRecent).text = if (recent.isEmpty()) "暂无记录"
        else recent.joinToString("\n") { log ->
            val dt = Instant.ofEpochMilli(log.time).atZone(zone)
                .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
            "${if (log.taken) "✔" else "✘"} ${log.title}  $dt"
        }
    }

    /** 计算 reminder 在指定日期会触发的次数（过未来时间点不计） */
    private fun countOccurrencesOn(r: Reminder, date: LocalDate): Int {
        val now = java.time.LocalDateTime.now()
        return when (r.repeatType) {
            RepeatType.ONCE ->
                if (OccurrenceCalculator.parseDate(r.startDate) == date) 1 else 0
            RepeatType.DAILY -> {
                if (date.isBefore(OccurrenceCalculator.parseDate(r.startDate))) 0
                else r.timesOfDay.count { t ->
                    date.atTime(OccurrenceCalculator.parseTime(t)).isBefore(now)
                }
            }
            RepeatType.WEEKLY -> {
                if (date.isBefore(OccurrenceCalculator.parseDate(r.startDate)) ||
                    date.dayOfWeek.value !in r.weekDays
                ) 0
                else r.timesOfDay.count { t ->
                    date.atTime(OccurrenceCalculator.parseTime(t)).isBefore(now)
                }
            }
            RepeatType.INTERVAL -> {
                val start = OccurrenceCalculator.parseDate(r.startDate)
                val step = r.intervalDays.coerceAtLeast(1).toLong()
                if (date.isBefore(start)) 0
                else {
                    val days = java.time.temporal.ChronoUnit.DAYS.between(start, date)
                    if (days % step != 0L) 0
                    else if (date.atTime(OccurrenceCalculator.parseTime(r.startTime)).isBefore(now)) 1 else 0
                }
            }
        }
    }

    private fun toggle(r: Reminder) {
        val list = ReminderStore.loadReminders(this)
        val idx = list.indexOfFirst { it.id == r.id }
        if (idx >= 0) {
            list[idx] = list[idx].copy(enabled = !list[idx].enabled)
            ReminderStore.saveReminders(this, list)
        }
        AlarmScheduler.rescheduleAll(this)
        refresh()
    }

    private fun confirmDelete(r: Reminder) {
        AlertDialog.Builder(this)
            .setTitle("删除提醒")
            .setMessage("确定删除「${r.title}」？")
            .setPositiveButton("删除") { _, _ ->
                AlarmScheduler.cancel(this, r.id)
                val list = ReminderStore.loadReminders(this)
                list.removeAll { it.id == r.id }
                ReminderStore.saveReminders(this, list)
                refresh()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun requestNeededPermissions() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            AlertDialog.Builder(this)
                .setTitle("需要闹钟权限")
                .setMessage("为了准点提醒，请在接下来的页面允许“闹钟和提醒”。")
                .setPositiveButton("去设置") { _, _ ->
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                    )
                }
                .setNegativeButton("稍后", null)
                .show()
        }
    }
}
