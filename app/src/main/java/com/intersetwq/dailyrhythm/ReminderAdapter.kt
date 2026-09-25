package com.intersetwq.dailyrhythm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ReminderAdapter(
    private val onToggle: (Reminder) -> Unit,
    private val onEdit: (Reminder) -> Unit,
    private val onDelete: (Reminder) -> Unit,
    private val onPhoto: (Reminder) -> Unit
) : RecyclerView.Adapter<ReminderAdapter.VH>() {

    private val items = mutableListOf<Reminder>()

    fun submit(list: List<Reminder>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvTitle: TextView = v.findViewById(R.id.tvTitle)
        val tvDesc: TextView = v.findViewById(R.id.tvDesc)
        val swEnabled: Switch = v.findViewById(R.id.swEnabled)
        val btnDelete: ImageButton = v.findViewById(R.id.btnDelete)
        val ivPhoto: ImageView = v.findViewById(R.id.ivPhoto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_reminder, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = items[position]
        h.tvTitle.text = r.title
        h.tvDesc.text = describe(r)
        h.swEnabled.setOnCheckedChangeListener(null)
        h.swEnabled.isChecked = r.enabled
        h.swEnabled.setOnCheckedChangeListener { _, _ -> onToggle(r) }
        h.itemView.setOnClickListener { onEdit(r) }
        h.btnDelete.setOnClickListener { onDelete(r) }
        h.ivPhoto.visibility = if (r.photoName.isBlank()) View.GONE else View.VISIBLE
        h.ivPhoto.setOnClickListener { onPhoto(r) }
    }

    private fun describe(r: Reminder): String {
        val sb = StringBuilder()
        when (r.repeatType) {
            RepeatType.ONCE -> sb.append("仅 ").append(r.startDate).append(" ").append(r.startTime)
            RepeatType.DAILY -> sb.append("每天 ").append(r.timesOfDay.joinToString("、"))
            RepeatType.WEEKLY -> {
                val names = listOf("", "一", "二", "三", "四", "五", "六", "日")
                sb.append("每").append(r.weekDays.sorted().joinToString("、") { names[it] })
                    .append(" ").append(r.timesOfDay.joinToString("、"))
            }
            RepeatType.INTERVAL -> sb.append("每 ").append(r.intervalDays).append(" 天 ").append(r.startTime)
        }
        if (r.strength == AlarmStrength.FULL_ALARM) sb.append(" · 强闹钟")
        if (r.note.isNotBlank()) sb.append(" · ").append(r.note)
        if (!r.enabled) sb.append("（已停用）")
        return sb.toString()
    }
}
