# AGENTS.md — DailyRhythm (Daily Routine Reminder Android App)

Native Android app (Kotlin + View system, no Compose, no third-party UI frameworks).

## Build Commands

- This machine has **no standalone Gradle and no working gradlew** (the repo commits only the wrapper properties, not the wrapper jar). Use the local cached Gradle directly:
  `C:/Users/d111k/.gradle/wrapper/dists/gradle-8.14.4-bin/92wwslzcyst3phie3o264zltu/gradle-8.14.4/bin/gradle assembleDebug --no-daemon -q`
- Invoke via the call operator with the `.bat` file (`& "C:\...\gradle.bat"`); the bare extensionless binary silently fails with no output. **A successful build prints nothing**; the APK lands in `app/build/outputs/apk/debug/app-debug.apk`.
- Requires JDK 17; `compileSdk 35` / `minSdk 26` / `targetSdk 35`. The SDK path is hardcoded in `local.properties` (`sdk.dir=D:\androidSDK`), which is gitignored — recreate it on a new machine.
- Dependencies resolve through Aliyun mirrors (`settings.gradle.kts`). No tests, no lint config — verification = build succeeds + manual device testing.

## Architecture (single module, all in `app/src/main/java/com/intersetwq/dailyrhythm/`)

- **Data layer**: `ReminderStore` (stores `files/reminders.json` and `dose_logs.json`; no Room/SQLite), `ReminderJson` (the **only** codec for reminders: **v2 grouped format** `{"title","date","repeat":{"type":...},...}` that serializes only the fields each repeat mode needs; lenient parsing that skips bad entries with per-entry errors; also reads legacy v1 flat data for migration — internal storage and import/export share this codec; `dose_logs.json` still goes through plain Gson), `SettingsStore` (SharedPreferences; keys: default strong reminder / notification sound / snooze duration / timeline batch dequeue / sort direction / theme mode), `Reminder.kt` (model; times stored as `HH:mm` strings following a "minutes since local midnight" convention).
- **Scheduling core**: `OccurrenceCalculator` (pure next-trigger computation for the four repeat modes, no Android dependencies — change scheduling logic here first), `AlarmScheduler` (exact alarms via `AlarmManager.setExactAndAllowWhileIdle`/`setAlarmClock`, rolls the next occurrence after firing), `AlarmReceiver`/`BootReceiver`.
- **Reminder presentation**: the `strength` field routes to a normal notification (`AlarmNotifier`) vs a fullscreen alarm (`AlarmActivity`).
- **UI**: MainActivity is a single activity hosting four inner tabs (reminder list / stats / timeline / settings) — stats and timeline are embedded views, not separate activities; `EditReminderActivity` and `AlarmActivity` are standalone. Import/export and theme switching (`AppCompatDelegate.setDefaultNightMode`) live in MainActivity. Import is a **two-step dialog** (preview → choose merge mode); do not merge `setMessage` and `setItems` into one dialog — on some ROMs the items list silently fails to render. The settings page also has an "AI generate" card with two clipboard-Prompt buttons: `生成 Prompt` (permanent, generates v2 JSON without id/createdAt/photo) and `旧格式转新` (transitional legacy→v2 converter, marked `TODO` for removal in a future version).

## Visual conventions

- Colors follow GitHub Mobile (Primer): light `#F6F8FA` canvas + white cards + `#0969DA` accent; dark (`values-night`) `#0D1117` canvas + `#161B22` cards + `#58A6FF` accent.
- **All colors must go through `@color/` semantic references** (`bg_page`/`card_bg`/`text_primary`/`text_secondary`/`accent_blue`/`on_brand`/`danger` etc.). Never hardcode hex values in layouts or code, or dark mode will break. The fullscreen alarm page (`activity_alarm.xml`) is the deliberate exception — it stays black.
- The header bar (main `headerBar`, edit-page `topBar`) is brand-blue with content `wrap_content + minHeight 72dp` + 12dp bottom padding, padded under the status bar via `SystemBarsHelper.applyWithHeader` — **insets must attach to the outermost header container**; attaching them to the inner title clips the sibling buttons under the status bar.

## Project-specific gotchas

- **targetSdk 35 (Android 15) enforces edge-to-edge**: `window.statusBarColor` is dead — never use it. Status-bar handling goes through `SystemBarsHelper` (insets + header padding). Every new screen must adopt this, or white status-bar icons will vanish against light backgrounds.
- **Navigation must not rely on gestures/physical back**: every screen needs an on-screen button (bottom tabs, an explicit "← Back" on the edit page). This is a hard usability requirement, not a style preference.
- The FAB lives inside the reminders-page `FrameLayout` (not on a `CoordinatorLayout` root) so it does not cover the bottom navigation.
- The test emulator `Medium_Phone_API_36.1` runs with a multidisplay secondary screen; `adb screencap` captures the secondary desktop instead of the app — verify UI via `uiautomator dump` (bounds/text), not screenshots.
- Start the emulator with the Windows scheduled task `DailyRhythmEmulator` (`schtasks //Run //TN "DailyRhythmEmulator"` — double slashes under Git Bash). Bash session timeouts kill child processes; never start the emulator in the background from bash.
- `adb push`/`shell` absolute paths get rewritten to Windows paths by Git Bash — prefix commands with `MSYS_NO_PATHCONV=1`.
- To seed test data: `adb push` the JSON to `/data/local/tmp`, then `run-as com.intersetwq.dailyrhythm cp` into `files/`, then broadcast `BOOT_COMPLETED` to reschedule.
- On some devices (e.g. Honor), newly pushed files do not appear in the Storage Access Framework file picker until scanned — trigger with `adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/<name>`.

## Maintenance rules

When the project structure, build/test commands, architecture boundaries, conventions, or any other recorded fact changes, update this file in the same change.
