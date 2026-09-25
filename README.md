# 用药提醒 (MedReminder)

原生 Android（Kotlin）提醒应用：按时吃药、规律作息、学习计划提醒。

## 功能

- **四种重复模式**：每天（可一天多个时间点）/ 按星期 / 每隔 N 天（如两天一次的药）/ 仅一次
- **两种提醒强度（每条可选）**：
  - 普通通知：通知栏 + 声音震动，通知上可直接点“已服用”
  - 强提醒：全屏闹钟，锁屏亮屏响铃，需点“已服用 / 10分钟后再提醒 / 跳过”
- **服药记录与统计**：每次打卡留痕，统计页显示近 7 天按时率与最近记录
- **药盒拍照**：编辑提醒时拍药盒照片防拿错药，列表点相册图标可查看
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
2. 强提醒需允许“显示在其他应用上层 / 全屏意图”（部分国产 ROM 需在电池设置中允许后台运行）

## 技术要点

- `AlarmManager.setExactAndAllowWhileIdle` / `setAlarmClock` 精确触发，触发后滚动排下一次
- 数据用 Gson JSON 存于 `files/`（`reminders.json`、`dose_logs.json`），照片存 `cache/photos/`
- 纯 Kotlin + View 体系，无第三方 UI 框架，minSdk 26 / targetSdk 35
