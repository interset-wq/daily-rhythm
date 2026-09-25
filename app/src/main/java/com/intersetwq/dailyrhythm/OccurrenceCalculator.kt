package com.intersetwq.dailyrhythm

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 计算“某条提醒下一次应该触发的时间”。
 * 所有计算基于设备本地时间。
 */
object OccurrenceCalculator {

    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /** yyyy-MM-dd -> LocalDate */
    fun parseDate(s: String): LocalDate = LocalDate.parse(s, dateFmt)

    /**
     * 批量计算未来触发点：每条提醒反复取“下一次”直到 [until]，
     * 汇总后按时间升序返回（时间, 提醒）列表。
     */
    fun upcoming(reminders: List<Reminder>, now: LocalDateTime, until: LocalDateTime): List<Pair<LocalDateTime, Reminder>> {
        val out = mutableListOf<Pair<LocalDateTime, Reminder>>()
        for (r in reminders) {
            if (!r.enabled) continue
            var cursor = now
            // 单条提醒最多收集 30 个点，防止异常数据死循环
            var guard = 0
            while (guard++ < 30) {
                val next = nextAfter(r, cursor) ?: break
                if (next.isAfter(until)) break
                out.add(next to r)
                cursor = next
            }
        }
        return out.sortedBy { it.first }
    }

    /** HH:mm -> LocalTime */
    fun parseTime(s: String): LocalTime =
        LocalTime.parse(if (s.length == 5) s else "0$s", DateTimeFormatter.ofPattern("HH:mm"))

    /**
     * 返回严格晚于 [after] 的下一次触发时间；无下一次（如 ONCE 已过、星期全未选）返回 null。
     */
    fun nextAfter(r: Reminder, after: LocalDateTime): LocalDateTime? {
        if (!r.enabled) return null
        return when (r.repeatType) {
            RepeatType.ONCE -> {
                val dt = LocalDateTime.of(parseDate(r.startDate), parseTime(r.startTime))
                if (dt.isAfter(after)) dt else null
            }
            RepeatType.DAILY -> {
                val start = parseDate(r.startDate)
                val times = r.timesOfDay.map { parseTime(it) }.sorted()
                // 从开始日期起，逐天找第一个含未来时间点的日期（最多看 7 天足够）
                var d = if (start.isAfter(after.toLocalDate())) start else after.toLocalDate()
                for (i in 0..7) {
                    val day = d.plusDays(i.toLong())
                    if (day.isBefore(start)) continue
                    for (t in times) {
                        val dt = LocalDateTime.of(day, t)
                        if (dt.isAfter(after)) return dt
                    }
                }
                null
            }
            RepeatType.WEEKLY -> {
                if (r.weekDays.isEmpty()) return null
                val start = parseDate(r.startDate)
                val times = r.timesOfDay.map { parseTime(it) }.sorted()
                var d = if (start.isAfter(after.toLocalDate())) start else after.toLocalDate()
                for (i in 0..13) {
                    val day = d.plusDays(i.toLong())
                    if (day.isBefore(start)) continue
                    // ISO: 1=周一 … 7=周日
                    if (day.dayOfWeek.value !in r.weekDays) continue
                    for (t in times) {
                        val dt = LocalDateTime.of(day, t)
                        if (dt.isAfter(after)) return dt
                    }
                }
                null
            }
            RepeatType.INTERVAL -> {
                val start = parseDate(r.startDate)
                val t = parseTime(r.startTime)
                val first = LocalDateTime.of(start, t)
                val step = r.intervalDays.coerceAtLeast(1).toLong()
                if (first.isAfter(after)) return first
                // 天数差向上取整到步长的倍数
                val daysFromStart = java.time.temporal.ChronoUnit.DAYS.between(start, after.toLocalDate())
                var k = daysFromStart / step + 1
                // 处理同一天但时间点已过的情况
                if (LocalDateTime.of(start.plusDays(k * step), t).isBefore(after)) k += 1
                LocalDateTime.of(start.plusDays(k * step), t)
            }
        }
    }
}
