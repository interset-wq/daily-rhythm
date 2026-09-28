package com.intersetwq.dailyrhythm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 时间线列表（课程表式纵向时间轴）：
 * 行类型 0 = 日期头（今天/明天/周几），行类型 1 = 触发点卡片。
 */
class TimelineAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed interface Row
    private data class DayRow(val date: LocalDate, val label: String, val dateText: String) : Row
    /** state: 0=已完成 1=已跳过 2=已过期未打卡 3=未到点 */
    private data class EventRow(val time: LocalDateTime, val reminder: Reminder, val state: Int) : Row

    private val rows = mutableListOf<Row>()

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFmt = DateTimeFormatter.ofPattern("MM-dd EEE")

    /**
     * [logs] 用于判定"今天已到点"条目的打卡状态：已完成/已跳过渲染为灰色，
     * 已过期未打卡同样置灰；条目保留到当日结束，0:00 随新一天视图批量出队。
     * 打卡与触发点按 (reminderId, 触发时刻 ±1 小时) 匹配，容忍打卡延迟。
     */
    fun submit(list: List<Pair<LocalDateTime, Reminder>>, logs: List<DoseLog> = emptyList()) {
        rows.clear()
        val now = LocalDateTime.now()
        // reminderId -> 该提醒所有打卡的 epoch millis
        val logTimes = logs.groupBy({ it.reminderId }, { it.time })
        var lastDay: LocalDate? = null
        for ((time, r) in list) {
            val day = time.toLocalDate()
            if (day != lastDay) {
                val today = LocalDate.now()
                val label = when (day) {
                    today -> "今天"
                    today.plusDays(1) -> "明天"
                    else -> "周${"一二三四五六日"[day.dayOfWeek.value - 1]}"
                }
                rows.add(DayRow(day, label, day.format(dateFmt)))
                lastDay = day
            }
            val state = if (!time.isAfter(now)) {
                // 已到点：±1 小时窗口内的打卡视为该次触发
                val inWindow = logTimes[r.id].orEmpty().any {
                    val l = java.time.Instant.ofEpochMilli(it)
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                    !l.isBefore(time.minusHours(1)) && !l.isAfter(time.plusHours(1))
                }
                val takenLog = inWindow && logTimes[r.id]!!.any {
                    val l = java.time.Instant.ofEpochMilli(it)
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                    val log = logs.first { x ->
                        x.reminderId == r.id && java.time.Instant.ofEpochMilli(x.time)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() == l
                    }
                    log.taken
                }
                when {
                    inWindow && takenLog -> 0
                    inWindow -> 1
                    else -> 2
                }
            } else {
                3
            }
            rows.add(EventRow(time, r, state))
        }
        notifyDataSetChanged()
    }

    class DayVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvLabel: TextView = v.findViewById(R.id.tvDayLabel)
        val tvDate: TextView = v.findViewById(R.id.tvDayDate)
    }

    class EventVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvTime: TextView = v.findViewById(R.id.tvTime)
        val tvCountdown: TextView = v.findViewById(R.id.tvCountdown)
        val tvBadge: TextView = v.findViewById(R.id.tvBadge)
        val tvTitle: TextView = v.findViewById(R.id.tvTitle)
        val tvNote: TextView = v.findViewById(R.id.tvNote)
    }

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is DayRow) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            DayVH(inflater.inflate(R.layout.item_timeline_day, parent, false))
        } else {
            EventVH(inflater.inflate(R.layout.item_timeline, parent, false))
        }
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is DayRow -> {
                val h = holder as DayVH
                h.tvLabel.text = row.label
                h.tvDate.text = row.dateText
            }
            is EventRow -> {
                val h = holder as EventVH
                val gray = row.state != 3
                h.tvTime.text = row.time.format(timeFmt)
                // 倒计时位改为状态文案；未到点才显示剩余时间
                h.tvCountdown.text = when (row.state) {
                    0 -> "已完成"
                    1 -> "已跳过"
                    2 -> "已过期"
                    else -> countdown(row.time)
                }
                // 已到点条目整体置灰（0:00 随当日结束批量出队）
                val grayColor = 0xFF9AA3AD.toInt()
                val normalColor = 0xFF1565C0.toInt()
                h.tvTime.setTextColor(if (gray) grayColor else normalColor)
                h.tvCountdown.setTextColor(grayColor)
                h.tvTitle.setTextColor(if (gray) grayColor else 0xFF1C1F26.toInt())
                h.tvNote.setTextColor(grayColor)
                h.tvBadge.alpha = if (gray) 0.4f else 1f
                h.tvBadge.visibility = if (row.reminder.strength == AlarmStrength.FULL_ALARM) View.VISIBLE else View.GONE
                h.tvTitle.text = row.reminder.title
                if (row.reminder.note.isNotBlank()) {
                    h.tvNote.visibility = View.VISIBLE
                    h.tvNote.text = row.reminder.note
                } else {
                    h.tvNote.visibility = View.GONE
                }
            }
        }
    }

    private fun countdown(target: LocalDateTime): String {
        val mins = Duration.between(LocalDateTime.now(), target).toMinutes()
        return when {
            mins < 1 -> "即将开始"
            mins < 60 -> "${mins}分钟后"
            mins < 60 * 24 -> "${mins / 60}小时${mins % 60}分后"
            else -> "${mins / (60 * 24)}天后"
        }
    }
}
