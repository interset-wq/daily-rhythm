package com.intersetwq.dailyrhythm

import android.content.Context

/**
 * 全局设置（SharedPreferences）：
 *  - defaultFullAlarm：新建提醒时默认是否开启强提醒
 *  - defaultSnooze：默认“稍后提醒”分钟数（应用于通知/闹钟的 10 分钟选项）
 *  - soundEnabled：普通通知是否带声音
 */
object SettingsStore {

    private const val PREFS = "app_settings"

    const val KEY_DEFAULT_FULL_ALARM = "default_full_alarm"
    const val KEY_SNOOZE_MINUTES = "snooze_minutes"
    const val KEY_SOUND_ENABLED = "sound_enabled"
    /** 时间线出队方式：true=当日结束批量出队（默认），false=提醒后立即出队 */
    const val KEY_TIMELINE_BATCH_DEQUEUE = "timeline_batch_dequeue"
    /** 提醒列表排序：true=按触发时间升序（默认），false=降序；多个触发时间取最早的一个 */
    const val KEY_REMINDER_SORT_ASC = "reminder_sort_asc"
    /** 外观模式：0=跟随系统（默认）1=浅色 2=深色 */
    const val KEY_THEME_MODE = "theme_mode"

    fun defaults(ctx: Context): android.content.SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 新建提醒的默认强度 */
    fun defaultFullAlarm(ctx: Context): Boolean =
        defaults(ctx).getBoolean(KEY_DEFAULT_FULL_ALARM, false)

    /** 稍后提醒分钟数（默认 10） */
    fun snoozeMinutes(ctx: Context): Int =
        defaults(ctx).getInt(KEY_SNOOZE_MINUTES, 10)

    /** 普通通知是否发声（默认开） */
    fun soundEnabled(ctx: Context): Boolean =
        defaults(ctx).getBoolean(KEY_SOUND_ENABLED, true)

    /** 时间线是否当日结束批量出队（默认 true） */
    fun timelineBatchDequeue(ctx: Context): Boolean =
        defaults(ctx).getBoolean(KEY_TIMELINE_BATCH_DEQUEUE, true)

    /** 提醒列表是否按下次触发时间升序（默认 true） */
    fun reminderSortAsc(ctx: Context): Boolean =
        defaults(ctx).getBoolean(KEY_REMINDER_SORT_ASC, true)

    /** 外观模式：0=跟随系统（默认）1=浅色 2=深色 */
    fun themeMode(ctx: Context): Int =
        defaults(ctx).getInt(KEY_THEME_MODE, 0)
}
