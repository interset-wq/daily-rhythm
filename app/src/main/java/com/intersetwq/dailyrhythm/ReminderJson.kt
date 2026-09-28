package com.intersetwq.dailyrhythm

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.LocalDate

/**
 * 提醒 JSON 编解码（v2 分组格式）。
 *
 * 外部格式（B1 模式分组，按需字段）：
 * ```
 * {"title":"吃药","date":"2026-09-28","note":"饭后",
 *  "repeat":{"type":"daily","times":["08:00","20:00"]},"strength":"alarm"}
 * ```
 * - 公共字段：title(必填)、date、note、strength、enabled、photo、id、createdAt
 * - 模式对象 repeat：daily{times} / weekly{days,times} / interval{every,time} / once{time}
 * - id/createdAt/photo 缺省自动补齐（AI 生成的 JSON 不需要写这些）
 * - 未知字段忽略，坏条目跳过不炸整批（手编/AI 容错）
 * - 兼容读入 v1 平铺格式（repeatType/timesOfDay 平铺），仅供内部旧数据迁移，新输出一律 v2
 */
object ReminderJson {

    private val gson = Gson()
    private val TIME_RE = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
    private val DATE_RE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    /** 解析结果：有效条目 + 逐条失败原因（"第 N 条：…"） */
    data class ParseResult(
        val reminders: MutableList<Reminder>,
        val errors: MutableList<String>
    )

    fun toJson(list: List<Reminder>): String {
        val arr = JsonArray()
        list.forEach { arr.add(encode(it)) }
        return gson.toJson(arr)
    }

    private fun encode(r: Reminder): JsonObject {
        val o = JsonObject()
        o.addProperty("id", r.id)
        o.addProperty("title", r.title)
        if (r.note.isNotEmpty()) o.addProperty("note", r.note)
        o.addProperty("date", r.startDate)
        o.add("repeat", encodeRepeat(r))
        if (r.strength == AlarmStrength.FULL_ALARM) o.addProperty("strength", "alarm")
        if (!r.enabled) o.addProperty("enabled", false)
        if (r.photoName.isNotEmpty()) o.addProperty("photo", r.photoName)
        o.addProperty("createdAt", r.createdAt)
        return o
    }

    private fun encodeRepeat(r: Reminder): JsonObject {
        val rep = JsonObject()
        when (r.repeatType) {
            RepeatType.DAILY -> {
                rep.addProperty("type", "daily")
                rep.add("times", strArray(r.timesOfDay))
            }
            RepeatType.WEEKLY -> {
                rep.addProperty("type", "weekly")
                rep.add("days", intArray(r.weekDays))
                rep.add("times", strArray(r.timesOfDay))
            }
            RepeatType.INTERVAL -> {
                rep.addProperty("type", "interval")
                rep.addProperty("every", r.intervalDays)
                rep.addProperty("time", r.startTime)
            }
            RepeatType.ONCE -> {
                rep.addProperty("type", "once")
                rep.addProperty("time", r.startTime)
            }
        }
        return rep
    }

    private fun strArray(v: List<String>): JsonArray =
        JsonArray().also { a -> v.forEach { a.add(it) } }

    private fun intArray(v: List<Int>): JsonArray =
        JsonArray().also { a -> v.forEach { a.add(it) } }

    /**
     * 宽松解析：接受 v2 数组 / v2 单对象 / v1 平铺数组。
     * 坏条目跳过并记入 [ParseResult.errors]，有效条目照常返回。
     */
    fun fromJson(json: String): ParseResult {
        val result = ParseResult(mutableListOf(), mutableListOf())
        val root = runCatching { JsonParser.parseString(json) }.getOrElse {
            result.errors.add("整体：不是有效的 JSON")
            return result
        }
        val arr = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject -> JsonArray().apply { add(root) }
            else -> {
                result.errors.add("整体：需要 JSON 数组或对象")
                return result
            }
        }
        var nextId = System.currentTimeMillis()
        arr.forEachIndexed { i, el ->
            val label = "第 ${i + 1} 条"
            if (!el.isJsonObject) {
                result.errors.add("$label：不是 JSON 对象")
                return@forEachIndexed
            }
            val r = runCatching { decode(el.asJsonObject) { nextId++ } }
                .getOrElse { e ->
                    result.errors.add("$label：${e.message ?: "解析失败"}")
                    null
                }
            if (r != null) result.reminders.add(r)
        }
        return result
    }

    private fun decode(o: JsonObject, allocId: () -> Long): Reminder {
        val title = o.str("title")?.trim().orEmpty()
        if (title.isEmpty()) throw IllegalArgumentException("缺少 title")

        // v1 平铺格式识别（repeatType 存在）：旧内部数据迁移用
        val rep: JsonObject = if (o.has("repeatType")) migrateV1(o) else {
            val repEl = o.get("repeat")
            if (repEl == null || !repEl.isJsonObject) throw IllegalArgumentException("缺少 repeat 对象")
            repEl.asJsonObject
        }

        val type = rep.str("type")?.lowercase() ?: throw IllegalArgumentException("缺少 repeat.type")
        val date = o.str("date") ?: LocalDate.now().toString()
        if (!DATE_RE.matches(date)) throw IllegalArgumentException("date 格式应为 yyyy-MM-dd")

        val (repeatType, startTime, timesOfDay, weekDays, intervalDays) = when (type) {
            "daily" -> Five(
                RepeatType.DAILY, "08:00",
                timeList(rep, "times", required = true), listOf(1), 2
            )
            "weekly" -> Five(
                RepeatType.WEEKLY, "08:00",
                timeList(rep, "times", required = true),
                intList(rep, "days", 1..7, required = true), 2
            )
            "interval" -> Five(
                RepeatType.INTERVAL,
                timeStr(rep, "time", required = true),
                listOf("08:00"), listOf(1),
                (rep.int("every") ?: throw IllegalArgumentException("缺少 repeat.every"))
                    .coerceAtLeast(2)
            )
            "once" -> Five(
                RepeatType.ONCE,
                timeStr(rep, "time", required = true),
                listOf("08:00"), listOf(1), 2
            )
            else -> throw IllegalArgumentException("repeat.type 非法：$type")
        }

        val id = o.long("id")?.takeIf { it > 0 } ?: allocId()
        val strength = if (o.str("strength") == "alarm") AlarmStrength.FULL_ALARM else AlarmStrength.NOTIFICATION
        return Reminder(
            id = id,
            title = title,
            note = o.str("note") ?: "",
            repeatType = repeatType,
            startDate = date,
            startTime = startTime,
            timesOfDay = timesOfDay,
            weekDays = weekDays,
            intervalDays = intervalDays,
            strength = strength,
            photoName = o.str("photo") ?: o.str("photoName") ?: "",
            enabled = o.get("enabled")?.let { !it.isJsonPrimitive || !it.asJsonPrimitive.isBoolean || it.asBoolean } ?: true,
            createdAt = o.long("createdAt")?.takeIf { it > 0 } ?: id
        )
    }

    /** v1 平铺 → repeat 对象（仅迁移读入，字段一一对应） */
    private fun migrateV1(o: JsonObject): JsonObject {
        val rep = JsonObject()
        when (o.str("repeatType")?.lowercase()) {
            "daily" -> {
                rep.addProperty("type", "daily")
                rep.add("times", o.get("timesOfDay")?.deepCopy() ?: JsonArray().apply { add("08:00") })
            }
            "weekly" -> {
                rep.addProperty("type", "weekly")
                rep.add("days", o.get("weekDays")?.deepCopy() ?: JsonArray().apply { add(1) })
                rep.add("times", o.get("timesOfDay")?.deepCopy() ?: JsonArray().apply { add("08:00") })
            }
            "interval" -> {
                rep.addProperty("type", "interval")
                rep.addProperty("every", o.int("intervalDays") ?: 2)
                rep.addProperty("time", o.str("startTime") ?: "08:00")
            }
            "once" -> {
                rep.addProperty("type", "once")
                rep.addProperty("time", o.str("startTime") ?: "08:00")
            }
            else -> throw IllegalArgumentException("repeatType 非法")
        }
        return rep
    }

    private data class Five(
        val t: RepeatType, val start: String, val times: List<String>,
        val days: List<Int>, val every: Int
    )

    // ===== 字段读取辅助（宽松容错） =====

    private fun JsonObject.str(k: String): String? =
        get(k)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun JsonObject.long(k: String): Long? =
        get(k)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong

    private fun JsonObject.int(k: String): Int? = long(k)?.toInt()

    private fun timeStr(o: JsonObject, k: String, required: Boolean): String {
        val v = o.str(k) ?: if (required) throw IllegalArgumentException("缺少 repeat.$k") else "08:00"
        if (!TIME_RE.matches(v)) throw IllegalArgumentException("repeat.$k 应为 HH:mm：$v")
        return v
    }

    private fun timeList(o: JsonObject, k: String, required: Boolean): List<String> {
        val el = o.get(k)
        if (el == null || !el.isJsonArray || el.asJsonArray.size() == 0) {
            if (required) throw IllegalArgumentException("缺少 repeat.$k")
            return listOf("08:00")
        }
        return el.asJsonArray.mapIndexed { i, t ->
            val v = t.takeIf { it.isJsonPrimitive }?.asString
                ?: throw IllegalArgumentException("repeat.$k[$i] 不是字符串")
            if (!TIME_RE.matches(v)) throw IllegalArgumentException("repeat.$k[$i] 应为 HH:mm：$v")
            v
        }
    }

    private fun intList(o: JsonObject, k: String, range: IntRange, required: Boolean): List<Int> {
        val el = o.get(k)
        if (el == null || !el.isJsonArray || el.asJsonArray.size() == 0) {
            if (required) throw IllegalArgumentException("缺少 repeat.$k")
            return listOf(1)
        }
        return el.asJsonArray.mapIndexed { i, t ->
            val v = t.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
                ?: throw IllegalArgumentException("repeat.$k[$i] 不是整数")
            if (v !in range) throw IllegalArgumentException("repeat.$k[$i] 超出范围 $range：$v")
            v
        }.distinct()
    }
}
