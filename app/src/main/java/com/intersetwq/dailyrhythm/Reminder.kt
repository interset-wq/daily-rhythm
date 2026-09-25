package com.intersetwq.dailyrhythm

import com.google.gson.annotations.SerializedName

/** 提醒模式 */
enum class RepeatType {
    /** 每天固定时间（可含一天多个时间点） */
    @SerializedName("daily") DAILY,
    /** 按星期几重复 */
    @SerializedName("weekly") WEEKLY,
    /** 每隔 N 天一次（如两天一次的药） */
    @SerializedName("interval") INTERVAL,
    /** 仅一次 */
    @SerializedName("once") ONCE
}

/** 到点后的提醒强度 */
enum class AlarmStrength {
    /** 普通通知：通知栏 + 声音/震动 */
    @SerializedName("notification") NOTIFICATION,
    /** 强提醒：全屏闹钟，必须手动停止 */
    @SerializedName("alarm") FULL_ALARM
}

/**
 * 一条提醒。时间统一用“从当天 00:00 起的分钟数”表示，便于跨天计算。
 */
data class Reminder(
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String,
    @SerializedName("note") val note: String = "",
    @SerializedName("repeatType") val repeatType: RepeatType = RepeatType.DAILY,
    /** 起始日期，格式 yyyy-MM-dd（DAILY/WEEKLY 的首日；INTERVAL 的起点；ONCE 的日期） */
    @SerializedName("startDate") val startDate: String,
    /** ONCE/INTERVAL 专用：起始时间，格式 HH:mm */
    @SerializedName("startTime") val startTime: String = "08:00",
    /** DAILY 模式下一天中的多个时间点，HH:mm */
    @SerializedName("timesOfDay") val timesOfDay: List<String> = listOf("08:00"),
    /** WEEKLY 模式下选中的星期，1=周一 … 7=周日 */
    @SerializedName("weekDays") val weekDays: List<Int> = listOf(1),
    /** INTERVAL 模式：间隔天数（>=2 表示隔 N 天） */
    @SerializedName("intervalDays") val intervalDays: Int = 2,
    @SerializedName("strength") val strength: AlarmStrength = AlarmStrength.NOTIFICATION,
    /** 药盒照片文件名（存私有目录，空串表示无） */
    @SerializedName("photoName") val photoName: String = "",
    @SerializedName("enabled") val enabled: Boolean = true,
    @SerializedName("createdAt") val createdAt: Long = System.currentTimeMillis()
)
