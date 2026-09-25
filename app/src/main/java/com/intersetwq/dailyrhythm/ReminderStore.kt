package com.intersetwq.dailyrhythm

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/** 一次服药/作息打卡记录 */
data class DoseLog(
    val reminderId: Long,
    val title: String,
    /** 记录时间 epoch millis */
    val time: Long,
    /** true=已执行, false=跳过 */
    val taken: Boolean
)

/**
 * 轻量 JSON 持久化（Gson），存放于 files/ 目录。
 */
object ReminderStore {
    private const val FILE_REMINDERS = "reminders.json"
    private const val FILE_LOGS = "dose_logs.json"
    private const val MAX_LOGS = 2000

    private val gson = Gson()

    private fun remindersFile(ctx: Context): File = File(ctx.filesDir, FILE_REMINDERS)
    private fun logsFile(ctx: Context): File = File(ctx.filesDir, FILE_LOGS)

    @Synchronized
    fun loadReminders(ctx: Context): MutableList<Reminder> {
        val f = remindersFile(ctx)
        if (!f.exists()) return mutableListOf()
        return runCatching {
            val type = object : TypeToken<MutableList<Reminder>>() {}.type
            gson.fromJson<MutableList<Reminder>>(f.readText(), type) ?: mutableListOf()
        }.getOrDefault(mutableListOf())
    }

    @Synchronized
    fun saveReminders(ctx: Context, list: List<Reminder>) {
        remindersFile(ctx).writeText(gson.toJson(list))
    }

    @Synchronized
    fun loadLogs(ctx: Context): MutableList<DoseLog> {
        val f = logsFile(ctx)
        if (!f.exists()) return mutableListOf()
        return runCatching {
            val type = object : TypeToken<MutableList<DoseLog>>() {}.type
            gson.fromJson<MutableList<DoseLog>>(f.readText(), type) ?: mutableListOf()
        }.getOrDefault(mutableListOf())
    }

    @Synchronized
    fun saveLogs(ctx: Context, list: List<DoseLog>) {
        logsFile(ctx).writeText(gson.toJson(list.takeLast(MAX_LOGS)))
    }

    /** 追加一条日志 */
    @Synchronized
    fun addLog(ctx: Context, log: DoseLog) {
        val logs = loadLogs(ctx)
        logs.add(log)
        saveLogs(ctx, logs)
    }
}
