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
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: ReminderAdapter
    private lateinit var tvEmpty: TextView
    private lateinit var pageReminders: View
    private lateinit var pageSettings: View
    private lateinit var pageTimeline: View
    private lateinit var tvTitleBar: TextView
    private lateinit var fab: FloatingActionButton
    private lateinit var btnToggleAll: TextView

    companion object {
        private const val REQ_EXPORT = 1001
        private const val REQ_IMPORT_FILE = 1002
        private const val STATE_PAGE = "state_page"
    }

    /** 当前所在 Tab；切主题触发 Activity 重建时靠它恢复页面 */
    private var currentPage = "reminders"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 启动即应用保存的外观偏好（跟随系统/浅色/深色）
        applyThemeMode()
        setContentView(R.layout.activity_main)
        // 品牌蓝 header 垫在状态栏后面（Android 15 edge-to-edge 兼容）——
        // insets 加到整个 headerBar 上，保证标题与排序按钮都在状态栏之下；
        // 额外 12dp 下移标题区，使上下留白均衡（原先上紧下松）
        SystemBarsHelper.applyWithHeader(
            this, findViewById(R.id.headerBar), (12 * resources.displayMetrics.density + 0.5f).toInt()
        )

        tvEmpty = findViewById(R.id.tvEmpty)
        pageReminders = findViewById(R.id.pageReminders)
        pageSettings = findViewById(R.id.pageSettings)
        pageTimeline = findViewById(R.id.pageTimeline)
        tvTitleBar = findViewById(R.id.tvTitleBar)
        fab = findViewById(R.id.fab)

        // 排序切换按钮：升序/降序，偏好持久化
        val btnSort = findViewById<ImageView>(R.id.btnSort)
        btnSort.setOnClickListener {
            val asc = !SettingsStore.reminderSortAsc(this)
            SettingsStore.defaults(this).edit()
                .putBoolean(SettingsStore.KEY_REMINDER_SORT_ASC, asc).apply()
            btnSort.setImageResource(if (asc) R.drawable.ic_sort_asc else R.drawable.ic_sort_desc)
            refresh()
        }

        // 一键关闭/开启全部提醒：全部启用时显示“全部停用”，否则显示“全部启用”
        btnToggleAll = findViewById(R.id.btnToggleAll)
        btnToggleAll.setOnClickListener { toggleAll() }

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

        // 页面恢复优先（切主题会重建 Activity），否则回退到快捷方式/默认提醒页
        val startPage = savedInstanceState?.getString(STATE_PAGE) ?: when (intent?.action) {
            "com.intersetwq.dailyrhythm.OPEN_TIMELINE" -> "timeline"
            else -> "reminders"
        }
        // 桌面长按快捷菜单：新建提醒
        if (intent?.action == "com.intersetwq.dailyrhythm.NEW_REMINDER") {
            startActivity(Intent(this, EditReminderActivity::class.java))
        }

        setupSettingsPage()

        // 底部 Tab：屏幕按钮切换页面，不依赖手势
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_reminders -> {
                    showPage("reminders")
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
        // 先显式 showPage 再同步选中项：selectedItemId 与当前一致时不会触发监听
        showPage(startPage)
        nav.selectedItemId = when (startPage) {
            "timeline" -> R.id.nav_timeline
            "settings" -> R.id.nav_settings
            else -> R.id.nav_reminders
        }

        requestNeededPermissions()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PAGE, currentPage)
    }

    private fun showPage(page: String) {
        currentPage = page
        val reminders = page == "reminders"
        pageReminders.visibility = if (reminders) View.VISIBLE else View.GONE
        pageTimeline.visibility = if (page == "timeline") View.VISIBLE else View.GONE
        pageSettings.visibility = if (page == "settings") View.VISIBLE else View.GONE
        fab.visibility = if (reminders) View.VISIBLE else View.GONE
        // INVISIBLE 而非 GONE：占位保持 40dp 行高，四个 Tab 的 header 高度完全一致
        findViewById<ImageView>(R.id.btnSort).visibility =
            if (reminders) View.VISIBLE else View.INVISIBLE
        btnToggleAll.visibility =
            if (reminders && ReminderStore.loadReminders(this).isNotEmpty()) View.VISIBLE else View.INVISIBLE
        tvTitleBar.text = when (page) {
            "timeline" -> "时间线"
            "settings" -> "设置"
            else -> getString(R.string.app_name)
        }
    }

    /** 时间线：显示 N 个自然日的触发计划（N=设置项 1-7，默认 7，1=仅当天）；批量出队（默认）从今天 00:00 起算并标注打卡状态，立即出队则只显示未触发条目 */
    private fun refreshTimeline() {
        val now = java.time.LocalDateTime.now()
        val batch = SettingsStore.timelineBatchDequeue(this)
        val start = if (batch) java.time.LocalDate.now().atStartOfDay() else now
        val until = java.time.LocalDate.now().atStartOfDay()
            .plusDays(SettingsStore.timelineDays(this).toLong())
        val items = OccurrenceCalculator.upcoming(
            ReminderStore.loadReminders(this), start, until
        )
        // 打卡记录按 (reminderId, 触发时间同小时) 归并：用于时间线区分已完成/已跳过
        val logs = if (batch) ReminderStore.loadLogs(this) else emptyList()
        val rv = findViewById<RecyclerView>(R.id.rvTimeline)
        if (rv.adapter == null) {
            rv.layoutManager = LinearLayoutManager(this)
            rv.adapter = TimelineAdapter()
        }
        (rv.adapter as TimelineAdapter).submit(items, logs)
        findViewById<View>(R.id.tvTimelineEmpty).visibility =
            if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    /** 设置页：读写全局默认值 */
    private fun setupSettingsPage() {
        val swDefaultAlarm = findViewById<Switch>(R.id.swDefaultAlarm)
        val swSound = findViewById<Switch>(R.id.swSound)
        val actSnooze = findViewById<TextView>(R.id.actSnooze)
        val prefs = SettingsStore.defaults(this)

        swDefaultAlarm.isChecked = SettingsStore.defaultFullAlarm(this)
        swSound.isChecked = SettingsStore.soundEnabled(this)

        // “稍后提醒”时长：系统闹钟风格弹层（预设值+自定义），选择即生效
        val snoozeOptions = listOf(1, 3, 5, 10, 15, 20, 30, 60)
        actSnooze.text = "${SettingsStore.snoozeMinutes(this)} 分钟"
        actSnooze.setOnClickListener {
            val labels = snoozeOptions.map { "$it 分钟" }.toTypedArray()
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("“稍后提醒”时长")
                .setItems(labels + "自定义…") { _, which ->
                    if (which < labels.size) {
                        prefs.edit().putInt(SettingsStore.KEY_SNOOZE_MINUTES, snoozeOptions[which]).apply()
                        actSnooze.text = labels[which]
                    } else {
                        val input = android.widget.EditText(this).apply {
                            inputType = android.text.InputType.TYPE_CLASS_NUMBER
                            hint = "1-120"
                            setText(SettingsStore.snoozeMinutes(this@MainActivity).toString())
                        }
                        androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("自定义分钟数")
                            .setView(input)
                            .setPositiveButton("确定") { _, _ ->
                                val v = input.text.toString().toIntOrNull()?.coerceIn(1, 120) ?: 10
                                prefs.edit().putInt(SettingsStore.KEY_SNOOZE_MINUTES, v).apply()
                                actSnooze.text = "$v 分钟"
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                }
                .show()
        }

        swDefaultAlarm.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(SettingsStore.KEY_DEFAULT_FULL_ALARM, checked).apply()
        }
        swSound.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(SettingsStore.KEY_SOUND_ENABLED, checked).apply()
        }

        // 时间线批量出队开关（默认开）：切换后即时刷新时间线
        val swBatch = findViewById<Switch>(R.id.swBatchDequeue)
        swBatch.isChecked = SettingsStore.timelineBatchDequeue(this)
        swBatch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(SettingsStore.KEY_TIMELINE_BATCH_DEQUEUE, checked).apply()
            refreshTimeline()
        }

        // 时间线显示天数：1（仅当天）~ 7，选择后即时刷新时间线
        val actDays = findViewById<TextView>(R.id.actTimelineDays)
        fun syncDays() { actDays.text = "${SettingsStore.timelineDays(this)} 天" }
        syncDays()
        actDays.setOnClickListener {
            val labels = (1..7).map { if (it == 1) "1 天（仅当天）" else "$it 天" }.toTypedArray()
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("时间线显示天数")
                .setItems(labels) { _, which ->
                    prefs.edit().putInt(SettingsStore.KEY_TIMELINE_DAYS, which + 1).apply()
                    syncDays()
                    refreshTimeline()
                }
                .show()
        }

        // 导出：系统"保存文件"对话框；同时提供复制到剪贴板
        findViewById<TextView>(R.id.btnExport).setOnClickListener { exportReminders() }
        // 导入：系统文件选择器或粘贴文本，解析预览后按 追加/覆盖/替换 三模式写入
        findViewById<TextView>(R.id.btnImport).setOnClickListener { importReminders() }
        // AI 生成：复制内置 Prompt，让 AI 按导入格式生成提醒 JSON
        findViewById<TextView>(R.id.btnCopyPrompt).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("dailyrhythm_prompt", importPrompt()))
            android.widget.Toast.makeText(this, "Prompt 已复制，粘贴给 AI 即可", android.widget.Toast.LENGTH_SHORT).show()
        }

        // 外观三选一：跟随系统/浅色/深色，选择即写偏好并即时切换（无需重启）
        val actTheme = findViewById<TextView>(R.id.actTheme)
        val themeLabels = arrayOf("跟随系统", "浅色", "深色")
        val syncTheme = {
            actTheme.text = themeLabels[SettingsStore.themeMode(this).coerceIn(0, 2)]
        }
        syncTheme()
        actTheme.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("外观")
                .setItems(themeLabels) { _, which ->
                    prefs.edit().putInt(SettingsStore.KEY_THEME_MODE, which).apply()
                    syncTheme()
                    applyThemeMode()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        // 版本号
        runCatching {
            val ver = packageManager.getPackageInfo(packageName, 0).versionName
            findViewById<TextView>(R.id.tvVersion).text = "版本 $ver"
        }

        // 应用信息：跳系统应用详情页（通知/权限/存储/卸载）
        findViewById<View>(R.id.rowAppInfo).setOnClickListener {
            val i = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.fromParts("package", packageName, null))
            runCatching { startActivity(i) }
        }

        // 通知设置：跳系统通知页（横幅/声音/渠道重要性——渠道被 ROM 降级时在此改回"紧急"）
        findViewById<View>(R.id.rowNotifSettings).setOnClickListener {
            val i = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
            runCatching { startActivity(i) }
        }

        // 开源许可：展示 LICENSE（assets 内置，缺省给出仓库链接）
        findViewById<View>(R.id.rowLicense).setOnClickListener {
            val text = runCatching {
                assets.open("LICENSE").bufferedReader().use { it.readText() }
            }.getOrElse { "本项目使用 MIT 许可证发布。\n\nhttps://github.com/interset-wq/daily-rhythm" }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("开源许可")
                .setMessage(text)
                .setPositiveButton("关闭", null)
                .show()
        }

        // 功能介绍：折叠/展开
        val tvFeatures = findViewById<TextView>(R.id.tvFeatures)
        val tvToggle = findViewById<TextView>(R.id.tvFeaturesToggle)
        tvFeatures.visibility = View.GONE
        tvToggle.text = "展开 ▾"
        findViewById<View>(R.id.rowFeatures).setOnClickListener {
            val expanded = tvFeatures.visibility == View.VISIBLE
            tvFeatures.visibility = if (expanded) View.GONE else View.VISIBLE
            tvToggle.text = if (expanded) "展开 ▾" else "收起 ▴"
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
        val asc = SettingsStore.reminderSortAsc(this)
        // 按当天触发时间排序；一天有多个触发时间时取最早的一个
        fun firstTime(r: Reminder): java.time.LocalTime = when (r.repeatType) {
            RepeatType.ONCE, RepeatType.INTERVAL -> OccurrenceCalculator.parseTime(r.startTime)
            else -> r.timesOfDay.minOfOrNull { OccurrenceCalculator.parseTime(it) }
                ?: OccurrenceCalculator.parseTime(r.startTime)
        }
        val cmp = compareBy<Reminder>({ firstTime(it) }, { it.id })
        val list = ReminderStore.loadReminders(this).sortedWith(if (asc) cmp else cmp.reversed())
        adapter.submit(list)
        tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        // 同步一键开关按钮：有数据且在提醒页才显示；有任一启用→“全部停用”，否则“全部启用”
        btnToggleAll.text = if (list.any { it.enabled }) "全部停用" else "全部启用"
        btnToggleAll.visibility =
            if (list.isNotEmpty() && currentPage == "reminders") View.VISIBLE else View.INVISIBLE
    }

    /** 一键关闭/开启全部提醒：有任一启用则全部停用，全部停用则全部启用 */
    private fun toggleAll() {
        val list = ReminderStore.loadReminders(this)
        if (list.isEmpty()) return
        val target = list.none { it.enabled }
        for (i in list.indices) list[i] = list[i].copy(enabled = target)
        ReminderStore.saveReminders(this, list)
        AlarmScheduler.rescheduleAll(this)
        refresh()
    }

    /** 外观模式 → AppCompatDelegate 夜间模式，选择即时生效 */
    private fun applyThemeMode() {
        val mode = when (SettingsStore.themeMode(this)) {
            1 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            2 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
    }

    // ===== 导入/导出 =====

    /** 内置 Prompt：让 AI 按 v2 分组格式生成提醒 JSON（不含 id/createdAt/photo，系统自动分配） */
    private fun importPrompt(): String = """
        你是「每日节律 DailyRhythm」（Android 作息/用药提醒 App）的数据生成助手。用户会用自然语言描述想要的提醒，请生成可直接导入该 App 的 JSON 数组。

        规则：
        1. 只输出一个 JSON 数组，不要 Markdown 代码块标记，不要任何解释文字。
        2. 不要输出 id、createdAt、photo 字段——系统会自动生成，AI 无法知道这些值。
        3. 每条提醒的字段（可省字段仅在偏离默认值时输出，保持精简）：
        {
          "title": "喝药",            // 必填，提醒名称
          "date": "2026-09-29",       // 可省，yyyy-MM-dd 基准日期，缺省为今天
          "note": "饭后",             // 可省，备注，空则不写
          "repeat": { ... },          // 必填，模式对象（见下）
          "strength": "alarm",        // 可省，notification（默认，不写）/ alarm
          "enabled": false            // 可省，true（默认）不写，仅停用时写 false
        }
        4. repeat 四种模式，只写自己需要的字段：
        {"type":"daily","times":["08:00","20:00"]}              // 每天，times 至少 1 个
        {"type":"weekly","days":[1,2,3,4,5],"times":["21:00"]}  // 按星期，days 1=周一 … 7=周日
        {"type":"interval","every":2,"time":"08:00"}            // 每隔 N 天，every >= 2
        {"type":"once","time":"19:30"}                          // 仅一次
        5. 时间必须是 24 小时制 HH:mm（补零，如 08:05）；日期必须是 yyyy-MM-dd 且不早于今天（今天是 {TODAY}），用户没给日期就用今天。
        6. 不支持的周期（如每月一次）选最接近的模式，并在 note 中注明原意。
        7. 导入时选择「追加」模式，多条提醒之间无需任何关联。

        示例（每天两次吃药强提醒 / 每周三复盘 / 隔天维生素 / 一次性体检）：
        [{"title":"吃药","date":"{TODAY}","note":"饭后","repeat":{"type":"daily","times":["08:00","20:00"]},"strength":"alarm"},{"title":"周复盘","repeat":{"type":"weekly","days":[3],"times":["21:00"]}},{"title":"维生素","date":"{TODAY}","repeat":{"type":"interval","every":2,"time":"09:00"}},{"title":"体检","date":"{TODAY}","repeat":{"type":"once","time":"08:30"},"strength":"alarm"}]
    """.trimIndent().replace("{TODAY}", java.time.LocalDate.now().toString())

    private var pendingImport: List<Reminder>? = null

    private fun exportReminders() {
        val json = ReminderJson.toJson(ReminderStore.loadReminders(this))
        val choices = arrayOf("保存到文件…", "复制到剪贴板")
        AlertDialog.Builder(this)
            .setTitle("导出提醒（共 ${ReminderStore.loadReminders(this).size} 条）")
            .setItems(choices) { _, which ->
                when (which) {
                    0 -> {
                        pendingImport = null
                        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("application/json")
                            .putExtra(Intent.EXTRA_TITLE, "dailyrhythm_export.json")
                        startActivityForResult(i, REQ_EXPORT)
                    }
                    else -> {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("reminders", json))
                        android.widget.Toast.makeText(this, "已复制 JSON 到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun importReminders() {
        val choices = arrayOf("从文件选择…", "粘贴 JSON 文本")
        AlertDialog.Builder(this)
            .setTitle("导入提醒")
            .setItems(choices) { _, which ->
                when (which) {
                    0 -> startActivityForResult(
                        Intent(Intent.ACTION_OPEN_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("application/json"), REQ_IMPORT_FILE
                    )
                    else -> {
                        val input = android.widget.EditText(this).apply {
                            hint = "粘贴导出的 JSON 数组"
                            minLines = 4
                            gravity = android.view.Gravity.TOP
                        }
                        AlertDialog.Builder(this)
                            .setTitle("粘贴 JSON")
                            .setView(input)
                            .setPositiveButton("解析") { _, _ ->
                                showImportPreview(input.text.toString())
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 解析并弹预览：条数+标题列表，选模式写入（替换需二次确认） */
    private fun showImportPreview(json: String) {
        val result = ReminderJson.fromJson(json)
        val parsed = result.reminders
        if (parsed.isEmpty()) {
            val why = result.errors.take(3).joinToString("\n")
            android.widget.Toast.makeText(
                this,
                if (why.isEmpty()) "文件中没有提醒" else "解析失败：\n$why",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }
        val preview = parsed.joinToString("\n") { "• ${importPreviewLine(it)}" }
        val errNote = if (result.errors.isEmpty()) "" else
            "\n\n⚠ ${result.errors.size} 条无效已跳过：\n" + result.errors.take(3).joinToString("\n") +
                if (result.errors.size > 3) "\n…" else ""
        // 预览与模式选择拆成两段：部分 ROM 上 setMessage+setItems 共存时列表不渲染（导入模式选不了）
        AlertDialog.Builder(this)
            .setTitle("发现 ${parsed.size} 条提醒")
            .setMessage((if (preview.length > 1200) preview.take(1200) + "\n…" else preview) + errNote)
            .setPositiveButton("选择导入方式") { _, _ -> showImportModeDialog(parsed) }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 预览行：时间按模式取（daily/weekly 用 timesOfDay，interval/once 用 startTime） */
    private fun importPreviewLine(r: Reminder): String {
        val whenStr = when (r.repeatType) {
            RepeatType.DAILY, RepeatType.WEEKLY -> r.timesOfDay.joinToString("/")
            RepeatType.INTERVAL -> "每 ${r.intervalDays} 天 ${r.startTime}"
            RepeatType.ONCE -> "${r.startDate} ${r.startTime}"
        }
        return "${r.title}（$whenStr）"
    }

    /** 第二段：纯 setItems 选导入模式（替换需二次确认） */
    private fun showImportModeDialog(parsed: List<Reminder>) {
        val modes = arrayOf("追加到现有提醒", "按 id 覆盖（同 id 更新，新 id 追加）", "完全替换（清空后导入）")
        AlertDialog.Builder(this)
            .setTitle("导入方式")
            .setItems(modes) { _, which ->
                if (which == 2) {
                    AlertDialog.Builder(this)
                        .setTitle("确认完全替换？")
                        .setMessage("现有提醒将全部删除，且不可恢复（打卡记录保留）。")
                        .setPositiveButton("替换") { _, _ -> applyImport(parsed, which) }
                        .setNegativeButton("取消", null)
                        .show()
                } else {
                    applyImport(parsed, which)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun applyImport(parsed: List<Reminder>, mode: Int) {
        val current = ReminderStore.loadReminders(this)
        val next = when (mode) {
            0 -> { // 追加：id 冲突重新分配
                val used = current.map { it.id }.toMutableSet()
                current + parsed.map { r ->
                    if (r.id in used) {
                        var nid = System.currentTimeMillis()
                        while (nid in used) nid++
                        used.add(nid); r.copy(id = nid)
                    } else { used.add(r.id); r }
                }
            }
            1 -> { // 按 id 覆盖
                val byId = current.associateBy { it.id }.toMutableMap()
                parsed.forEach { byId[it.id] = it }
                byId.values.sortedBy { it.id }
            }
            else -> parsed // 完全替换
        }
        ReminderStore.saveReminders(this, next)
        AlarmScheduler.rescheduleAll(this)
        refresh()
        android.widget.Toast.makeText(this, "已导入 ${parsed.size} 条提醒", android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        when (requestCode) {
            REQ_EXPORT -> runCatching {
                contentResolver.openOutputStream(data.data!!)?.use { os ->
                    os.write(ReminderJson.toJson(ReminderStore.loadReminders(this)).toByteArray())
                }
                android.widget.Toast.makeText(this, "已导出", android.widget.Toast.LENGTH_SHORT).show()
            }.onFailure {
                android.widget.Toast.makeText(this, "导出失败：${it.message}", android.widget.Toast.LENGTH_LONG).show()
            }
            REQ_IMPORT_FILE -> runCatching {
                val json = contentResolver.openInputStream(data.data!!)?.use { it.readBytes().toString(Charsets.UTF_8) }
                if (json != null) showImportPreview(json)
            }.onFailure {
                android.widget.Toast.makeText(this, "读取失败：${it.message}", android.widget.Toast.LENGTH_LONG).show()
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
