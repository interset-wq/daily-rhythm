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
    private data class EventRow(val time: LocalDateTime, val reminder: Reminder) : Row

    private val rows = mutableListOf<Row>()

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFmt = DateTimeFormatter.ofPattern("MM-dd EEE")

    fun submit(list: List<Pair<LocalDateTime, Reminder>>) {
        rows.clear()
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
            rows.add(EventRow(time, r))
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
                h.tvTime.text = row.time.format(timeFmt)
                h.tvCountdown.text = countdown(row.time)
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
