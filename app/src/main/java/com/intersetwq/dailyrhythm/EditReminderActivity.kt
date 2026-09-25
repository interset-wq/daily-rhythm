package com.intersetwq.dailyrhythm

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 新建/编辑提醒。支持四种模式 + 每条选择提醒强度 + 拍药盒照片。
 */
class EditReminderActivity : AppCompatActivity() {

    private var editId: Long = -1L
    private fun nowTimeStr(): String =
        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

    private val times = mutableListOf(nowTimeStr())
    private val weekDays = linkedSetOf(1, 2, 3, 4, 5, 6, 7)
    private var startDate: LocalDate = LocalDate.now()
    private var photoName: String = ""

    private lateinit var rgType: RadioGroup
    private lateinit var rbDaily: RadioButton
    private lateinit var rbWeekly: RadioButton
    private lateinit var rbInterval: RadioButton
    private lateinit var rbOnce: RadioButton
    private lateinit var etTitle: EditText
    private lateinit var etNote: EditText
    private lateinit var tvTimesLabel: TextView
    private lateinit var hsTimes: View
    private lateinit var llTimes: LinearLayout
    private lateinit var llWeek: LinearLayout
    private lateinit var weekChecks: List<CheckBox>
    private lateinit var rowStart: View
    private lateinit var tvStartDate: TextView
    private lateinit var rowStart2: View
    private lateinit var tvStartTime: TextView
    private lateinit var etInterval: EditText
    private lateinit var rowInterval: View
    private lateinit var rowOnce: View
    private lateinit var tvOnceTime: TextView
    private lateinit var swAlarm: Switch
    private lateinit var btnPhoto: Button
    private lateinit var tvPhotoName: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)
        // 蓝色顶栏垫在状态栏后面（Android 15 edge-to-edge 兼容）
        SystemBarsHelper.applyWithHeader(this, findViewById(R.id.topBar))

        editId = intent.getLongExtra("id", -1L)
        bindViews()
        setupListeners()

        if (editId > 0) {
            ReminderStore.loadReminders(this).firstOrNull { it.id == editId }?.let { fill(it) }
            findViewById<TextView>(R.id.tvEditTitle).text = "编辑提醒"
        } else {
            findViewById<TextView>(R.id.tvEditTitle).text = "新建提醒"
            // 应用全局默认：新建时强提醒开关取设置页的默认值
            swAlarm.isChecked = SettingsStore.defaultFullAlarm(this)
            tvStartDate.text = startDate.toString()
            tvStartTime.text = nowTimeStr()
            tvOnceTime.text = nowTimeStr()
            refreshTypeUI()
        }
    }

    private fun bindViews() {
        rgType = findViewById(R.id.rgType)
        rbDaily = findViewById(R.id.rbDaily)
        rbWeekly = findViewById(R.id.rbWeekly)
        rbInterval = findViewById(R.id.rbInterval)
        rbOnce = findViewById(R.id.rbOnce)
        etTitle = findViewById(R.id.etTitle)
        etNote = findViewById(R.id.etNote)
        tvTimesLabel = findViewById(R.id.tvTimesLabel)
        hsTimes = findViewById(R.id.hsTimes)
        llTimes = findViewById(R.id.llTimes)
        llWeek = findViewById(R.id.llWeek)
        weekChecks = listOf(
            findViewById(R.id.cbMon), findViewById(R.id.cbTue), findViewById(R.id.cbWed),
            findViewById(R.id.cbThu), findViewById(R.id.cbFri), findViewById(R.id.cbSat), findViewById(R.id.cbSun)
        )
        rowStart = findViewById(R.id.rowStartDate)
        tvStartDate = findViewById(R.id.tvStartDate)
        rowStart2 = findViewById(R.id.rowStartTime)
        tvStartTime = findViewById(R.id.tvStartTime)
        etInterval = findViewById(R.id.etIntervalDays)
        rowInterval = findViewById(R.id.rowInterval)
        rowOnce = findViewById(R.id.rowOnce)
        tvOnceTime = findViewById(R.id.tvOnceTime)
        swAlarm = findViewById(R.id.swFullAlarm)
        btnPhoto = findViewById(R.id.btnTakePhoto)
        tvPhotoName = findViewById(R.id.tvPhotoName)
    }

    private fun setupListeners() {
        // 屏幕内返回按钮，不依赖手势/物理返回键
        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        rgType.setOnCheckedChangeListener { _, _ -> refreshTypeUI() }

        weekChecks.forEachIndexed { idx, cb ->
            cb.setOnCheckedChangeListener { _, checked ->
                if (checked) weekDays.add(idx + 1) else weekDays.remove(idx + 1)
            }
        }

        tvStartDate.setOnClickListener { pickDate() }
        tvStartTime.setOnClickListener { pickTime(tvStartTime) }
        tvOnceTime.setOnClickListener { pickTime(tvOnceTime) }

        btnPhoto.setOnClickListener { PhotoTaker.take(this, editId.takeIf { it > 0 } ?: pendingNewId()) }
        tvPhotoName.setOnClickListener {
            if (photoName.isNotBlank()) PhotoViewer.show(this, photoName)
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
        findViewById<Button>(R.id.btnDelete2).setOnClickListener {
            if (editId > 0) {
                AlarmScheduler.cancel(this, editId)
                val list = ReminderStore.loadReminders(this)
                list.removeAll { it.id == editId }
                ReminderStore.saveReminders(this, list)
            }
            finish()
        }
    }

    // 新建时先给一个临时 id（用时间戳），照片文件名跟着这个 id 走
    private fun pendingNewId(): Long = if (editId > 0) editId
    else LocalDateTime.now().toEpochSecond(java.time.ZoneOffset.UTC)

    private fun fill(r: Reminder) {
        etTitle.setText(r.title)
        etNote.setText(r.note)
        times.clear(); times.addAll(r.timesOfDay.ifEmpty { listOf(r.startTime) })
        weekDays.clear(); weekDays.addAll(r.weekDays)
        startDate = OccurrenceCalculator.parseDate(r.startDate)
        photoName = r.photoName
        when (r.repeatType) {
            RepeatType.DAILY -> rbDaily.isChecked = true
            RepeatType.WEEKLY -> rbWeekly.isChecked = true
            RepeatType.INTERVAL -> {
                rbInterval.isChecked = true
                etInterval.setText(r.intervalDays.toString())
            }
            RepeatType.ONCE -> rbOnce.isChecked = true
        }
        swAlarm.isChecked = r.strength == AlarmStrength.FULL_ALARM
        tvStartDate.text = r.startDate
        tvStartTime.text = r.startTime
        tvOnceTime.text = r.startTime
        if (photoName.isNotBlank()) tvPhotoName.text = "已拍摄 ✓"
        refreshTimesText()
        refreshTypeUI()
    }

    private fun refreshTypeUI() {
        val daily = rbDaily.isChecked
        val weekly = rbWeekly.isChecked
        val interval = rbInterval.isChecked
        val once = rbOnce.isChecked
        // DAILY/WEEKLY 用多时间点；INTERVAL/ONCE 用单时间
        tvTimesLabel.visibility = if (daily || weekly) View.VISIBLE else View.GONE
        hsTimes.visibility = if (daily || weekly) View.VISIBLE else View.GONE
        llWeek.visibility = if (weekly) View.VISIBLE else View.GONE
        rowInterval.visibility = if (interval) View.VISIBLE else View.GONE
        rowOnce.visibility = if (once) View.VISIBLE else View.GONE
        rowStart.visibility = if (once || interval) View.GONE else View.VISIBLE
        rowStart2.visibility = if (once || interval) View.VISIBLE else View.GONE
        refreshTimesText()
    }

    /** 渲染时间 chip 列表：点 chip 可修改/删除，末尾"+ 添加"新增 */
    private fun refreshTimesText() {
        llTimes.removeAllViews()
        for (t in times) {
            val chip = TextView(this).apply {
                text = t
                textSize = 15f
                setTextColor(0xFF1565C0.toInt())
                setBackgroundResource(R.drawable.bg_time_chip)
                setPadding(dp(14), dp(6), dp(14), dp(6))
                setOnClickListener { showTimeOptions(t) }
            }
            val lp = LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = dp(8)
            llTimes.addView(chip, lp)
        }
        // "+ 添加" chip
        val add = TextView(this).apply {
            text = "+ 添加"
            textSize = 15f
            setTextColor(0xFF546E7A.toInt())
            setBackgroundResource(R.drawable.bg_time_chip)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setOnClickListener { addTimePicker() }
        }
        val lp = LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        llTimes.addView(add, lp)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /** 点已有时间 chip：弹修改/删除选项 */
    private fun showTimeOptions(t: String) {
        val options = arrayOf("修改此时间", "删除此时间")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(t)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> editTime(t)
                    1 -> {
                        times.remove(t)
                        if (times.isEmpty()) times.add("08:00")
                        refreshTimesText()
                    }
                }
            }
            .show()
    }

    /** 修改某个已有时间点 */
    private fun editTime(t: String) {
        val old = OccurrenceCalculator.parseTime(t)
        val picker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
            .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_24H)
            .setHour(old.hour)
            .setMinute(old.minute)
            .setTitleText("修改提醒时间")
            .build()
        picker.addOnPositiveButtonClickListener {
            val v = String.format("%02d:%02d", picker.hour, picker.minute)
            if (v != t && v in times) {
                android.widget.Toast.makeText(this, "该时间已存在", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                val idx = times.indexOf(t)
                if (idx >= 0) { times[idx] = v; times.sort(); refreshTimesText() }
            }
        }
        picker.show(supportFragmentManager, "edit_time")
    }

    private fun addTimePicker() {
        val t = OccurrenceCalculator.parseTime(nowTimeStr())
        val picker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
            .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_24H)
            .setHour(t.hour)
            .setMinute(t.minute)
            .setTitleText("添加提醒时间")
            .build()
        picker.addOnPositiveButtonClickListener {
            val v = String.format("%02d:%02d", picker.hour, picker.minute)
            if (v in times) {
                android.widget.Toast.makeText(this, "该时间已存在", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                times.add(v); times.sort(); refreshTimesText()
            }
        }
        picker.show(supportFragmentManager, "add_time")
    }

    private fun pickTime(target: TextView) {
        val t = OccurrenceCalculator.parseTime(target.text.toString())
        val picker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
            .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_24H)
            .setHour(t.hour)
            .setMinute(t.minute)
            .setTitleText("选择时间")
            .build()
        picker.addOnPositiveButtonClickListener {
            target.text = String.format("%02d:%02d", picker.hour, picker.minute)
        }
        picker.show(supportFragmentManager, "pick_time")
    }

    private fun pickDate() {
        // Material 底部弹窗日期选择（返回 UTC 毫秒，需转本地时区）
        val picker = com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
            .setTitleText("选择开始日期")
            .setSelection(
                java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                    set(startDate.year, startDate.monthValue - 1, startDate.dayOfMonth, 0, 0, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
            )
            .build()
        picker.addOnPositiveButtonClickListener { utcMillis ->
            startDate = java.time.Instant.ofEpochMilli(utcMillis)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            tvStartDate.text = startDate.toString()
        }
        picker.show(supportFragmentManager, "pick_date")
    }

    private fun save() {
        val title = etTitle.text.toString().trim()
        if (title.isEmpty()) {
            etTitle.error = "请填写名称"
            return
        }
        val type = when {
            rbDaily.isChecked -> RepeatType.DAILY
            rbWeekly.isChecked -> RepeatType.WEEKLY
            rbInterval.isChecked -> RepeatType.INTERVAL
            else -> RepeatType.ONCE
        }
        if (type == RepeatType.WEEKLY && weekDays.isEmpty()) {
            android.widget.Toast.makeText(this, "请至少选择一个星期", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val list = ReminderStore.loadReminders(this)
        val old = list.firstOrNull { it.id == editId }
        val id = if (editId > 0) editId else (System.currentTimeMillis())

        val reminder = Reminder(
            id = id,
            title = title,
            note = etNote.text.toString().trim(),
            repeatType = type,
            startDate = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            startTime = if (type == RepeatType.DAILY || type == RepeatType.WEEKLY)
                times.first() else tvStartTime.text.toString().let { if (type == RepeatType.INTERVAL) it else tvOnceTime.text.toString() },
            timesOfDay = if (type == RepeatType.DAILY || type == RepeatType.WEEKLY) times.sorted() else listOf("08:00"),
            weekDays = weekDays.sorted(),
            intervalDays = if (type == RepeatType.INTERVAL) (etInterval.text.toString().toIntOrNull() ?: 2).coerceAtLeast(1) else 2,
            strength = if (swAlarm.isChecked) AlarmStrength.FULL_ALARM else AlarmStrength.NOTIFICATION,
            photoName = photoName,
            enabled = true,
            createdAt = old?.createdAt ?: System.currentTimeMillis()
        )

        list.removeAll { it.id == id }
        list.add(reminder)
        ReminderStore.saveReminders(this, list)
        AlarmScheduler.rescheduleAll(this)
        finish()
    }

    /** 拍照中暂存的文件名，onActivityResult 成功后落入 photoName */
    var pendingPhotoName: String? = null

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PhotoTaker.REQ_CODE) {
            val name = pendingPhotoName
            if (resultCode == RESULT_OK && name != null) {
                onPhotoTaken(name)
            }
            pendingPhotoName = null
        }
    }

    /** 拍照完成回调（由 PhotoTaker 调用） */
    fun onPhotoTaken(name: String) {
        photoName = name
        tvPhotoName.text = "已拍摄 ✓"
    }
}
