# AGENTS.md — MedReminder（用药提醒 Android 应用）

原生 Android 应用（Kotlin + View 体系，无 Compose、无第三方 UI 框架）。

## 构建命令

- 本机**没有**安装独立 Gradle，也没有可执行的 gradlew（仓库只提交了 wrapper properties，未提交 wrapper jar）。用本机缓存直接调用：
  `C:/Users/d111k/.gradle/wrapper/dists/gradle-8.14.4-bin/92wwslzcyst3phie3o264zltu/gradle-8.14.4/bin/gradle assembleDebug --no-daemon -q`
- 构建成功时**无任何输出**；产物在 `app/build/outputs/apk/debug/app-debug.apk`。
- SDK 路径写死在 `local.properties`（`sdk.dir=D:\androidSDK`），该文件被 gitignore，新环境需手动创建。
- 依赖走阿里云镜像（`settings.gradle.kts`）。无测试、无 lint 配置——验证手段就是编译通过 + 模拟器实测。

## 架构（单模块，全部在 `app/src/main/java/com/intersetwq/dailyrhythm/`）

- **数据层**：`ReminderStore`（Gson JSON 存 `files/reminders.json`、`dose_logs.json`，无 Room/SQLite）、`SettingsStore`（SharedPreferences）、`Reminder.kt`（模型，时间统一用"当天 00:00 起的分钟数"思想存 `HH:mm` 字符串）。
- **调度核心**：`OccurrenceCalculator`（四种重复模式的"下一次触发"纯计算，无 Android 依赖，改调度逻辑先改这里）、`AlarmScheduler`（AlarmManager 精确闹钟，触发后滚动排下一次）、`AlarmReceiver`/`BootReceiver`。
- **提醒呈现**：`strength` 字段决定走普通通知（`AlarmNotifier`）还是全屏闹钟（`AlarmActivity`）。
- **UI**：MainActivity 单 Activity 四个内页 Tab（提醒列表/统计/时间线/设置），统计与时间线是主界面内嵌 View，不是独立 Activity；`EditReminderActivity`、`AlarmActivity` 独立。

## 项目特有注意事项

- **targetSdk 35（Android 15）强制 edge-to-edge**：`window.statusBarColor` 已失效，禁止使用。状态栏处理统一走 `SystemBarsHelper`（insets + header 垫底）。新页面必须接入，否则白色状态栏图标会淹没在浅色背景里。
- **导航不依赖手势/物理按键**：所有页面必须有屏幕内按钮（底部 Tab、编辑页"← 返回"）。这是明确的可用性需求，不是风格偏好。
- **FAB 位于提醒页 FrameLayout 内**（不在 CoordinatorLayout 根上），避免遮挡底部导航。
- 模拟器 `Medium_Phone_API_36.1` 开了 multidisplay 副屏，`adb screencap` 会抓到副屏桌面；验证 UI 用 `uiautomator dump` 读 bounds/text，比截图可靠。
- 模拟器用 Windows 计划任务 `MedReminderEmulator` 启动（`schtasks //Run //TN "MedReminderEmulator"`，Git Bash 下必须双斜杠）。bash 会话超时会杀掉子进程，禁止用 bash 后台方式起模拟器。
- `adb push`/`shell` 的绝对路径会被 Git Bash 转成 Windows 路径，命令前加 `MSYS_NO_PATHCONV=1`。
- 塞测试数据：`adb push` JSON 到 `/data/local/tmp` 后用 `run-as com.intersetwq.dailyrhythm cp` 进 `files/`，再广播 BOOT_COMPLETED 触发重排。

## 维护规则

项目结构、构建/测试命令、架构边界、开发约定或本文件记录的其他事实发生变化时，必须在同一次改动中同步更新本文件。
