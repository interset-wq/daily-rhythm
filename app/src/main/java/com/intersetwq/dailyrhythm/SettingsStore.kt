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
}
