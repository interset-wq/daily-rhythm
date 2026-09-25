package com.intersetwq.dailyrhythm

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 状态栏处理（兼容 Android 15 edge-to-edge 强制模式）。
 *
 * targetSdk 35 下 `window.statusBarColor` 已被禁用（官方文档：deprecated and disabled），
 * 系统栏强制透明，内容绘制到状态栏后面。正确做法：
 *  1. 状态栏图标始终用白色（isAppearanceLightStatusBars = false）；
 *  2. 由页面顶部栏（品牌蓝）自然垫在状态栏下面 —— 用 insets 把 header 加高，
 *     非列表页则对内容施加顶部 padding。
 */
object SystemBarsHelper {

    /** 主界面：header 已是品牌蓝，让 header 延伸进状态栏即可（无闪烁、无色差）。 */
    fun applyWithHeader(activity: Activity, header: View) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = false // 白色图标（配深色 header）

        // 把 insets 的 top 换成 header 的 padding，使 header 垫到状态栏后面
        ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = bars.top)
            insets
        }
    }

    /** 通用页面：无品牌 header，白色/浅色内容区 —— 直接给根布局加状态栏高度 padding。 */
    fun apply(activity: Activity, root: View) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = false

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = bars.top)
            insets
        }
    }

    private fun View.updatePadding(top: Int) {
        setPadding(paddingLeft, top, paddingRight, paddingBottom)
    }
}
