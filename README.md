# Daily Rhythm（作息提醒）

原生 Android（Kotlin）提醒应用：为任何重复事项设置定时提醒——按时吃药、规律作息、学习计划等，一次配置，到点自动响，无需反复手动创建闹钟。

## 功能

- **四种重复模式**：每天（可一天多个时间点）/ 按星期 / 每隔 N 天 / 仅一次
- **两种提醒强度（每条可选）**：
  - 普通通知：通知栏 + 声音震动，通知上可直接打卡"完成"
  - 强提醒：全屏闹钟，锁屏亮屏响铃，需点"完成 / 稍后提醒 / 跳过"
- **时间线**：课程表式纵向时间轴，按天分组展示未来 7 天的触发计划与倒计时
- **打卡统计**：每次打卡留痕，统计页显示近 7 天按时率与最近记录
- **设置页**：默认提醒强度、通知声音、"稍后提醒"时长等全局默认值 + 应用信息
- **药盒拍照**：编辑提醒时拍照防拿错，列表点相册图标可查看
- 开机 / 应用更新后自动重排闹钟

## 构建

依赖：JDK 17、Android SDK（compileSdk 35）。SDK 路径写入 `local.properties` 的 `sdk.dir`。

```bash
gradle assembleDebug        # 产物在 app/build/outputs/apk/debug/app-debug.apk
```

## 安装与首次配置（重要）

1. 安装 APK 后打开，按提示授予：
   - **通知权限**（Android 13+）
   - **闹钟和提醒**权限（Android 12+，跳转系统设置开启，否则降级为非精确闹钟）
2. 强提醒需允许"显示在其他应用上层 / 全屏意图"（部分国产 ROM 需在电池设置中允许后台运行）

## 技术要点

- `AlarmManager.setExactAndAllowWhileIdle` / `setAlarmClock` 精确触发，触发后滚动排下一次
- 数据用 Gson JSON 存于 `files/`（`reminders.json`、`dose_logs.json`），设置存 SharedPreferences，照片存 `cache/photos/`
- 纯 Kotlin + View 体系，无第三方 UI 框架，minSdk 26 / targetSdk 35
- 包名 `com.intersetwq.dailyrhythm`（AI 代理请阅读 [AGENTS.md](AGENTS.md) 了解项目约定）
