# 恶魔闹铃 · 制作存档（v1.1 已交付）

## 目标
一个**真正关不掉的起床闹钟** Android App，交付可安装 **APK**（离线、自用）。
核心要求（用户原话）：「大退后也存在在状态栏，就像手机里的闹钟一样，只是没有了关闭闹铃按钮」。

**状态：v1.1 已交付，产出 `demon-alarm\demon-alarm.apk`（56.5 KB / 57898 字节，versionCode 2，v2+v3 签名）。**

## v1.1 变更（用户 m00321 实测反馈后）
用户报告：①新建「约 1 分钟后响」的闹钟，清后台后等 2 分钟没响；②新建闹钟要能选「响铃震动 / 仅震动」。

根因（静态审查得出，未真机验证）：
1. `AlarmReceiver.startRing` 把 `startForegroundService` 的异常 `catch (Exception ignore)` 吞掉 → Android 12+ 后台启动前台服务被拒时**静默不响**。
2. 排程用 `setExactAndAllowWhileIdle`，`SecurityException` 又静默降级 `am.set`（inexact，Doze 下可能拖很久）。
3. `nextTrigger` 容差只有 1 秒：用户选中的分钟若正好是当前分钟，`base <= now + 1000` 成立 → 直接推到**明天**。
4. 进程被 ROM 清理后没有任何组件能把闹钟重新排上。

修复（对应文件）：
- `AlarmStore`：新增 `arm()` 用 `setAlarmClock(AlarmClockInfo(t, showIntent))`（状态栏/锁屏可见、Doze 不延迟、AlarmClock 豁免后台启动前台服务），失败降级 `setExactAndAllowWhileIdle`→`am.set`；
  `schedule()` 在 t 与 t+20s 各排一颗（`BACKUP_REQUEST=3000+id`、`EXTRA_BACKUP=true`）；
  `nextTrigger(a, now, graceMs)` 支持 3 分钟宽限（`GRACE_MS`，宽限命中返回 `now+3000`）；
  `cancelBackup()`、`getSnoozeAt()`、`isWithinGrace()`（`scheduleAll` 跳过刚设的宽限闹钟，避免被重排推到明天）、
  `scheduleAll()`（跳过 5 分钟内即将响的）、`rescheduleAll()`（开机用，全部重排）、`nextFire/whenText/countdownText`（「今天 07:00」+「还有 8 小时」）、`setLastError/getLastError`、`markFired`。
- `AlarmReceiver`：备用闹钟去重（已在响同一 id、或暂停中 `getSnoozeAt>now` → 直接 return）；primary 触发先重排下一次；
  `startRing` 三级兜底（`startForegroundService` → `startService` → `RingService.postEmergency()` 发高优先级+全屏意图通知）；系统事件分支 `rescheduleAll` + `GuardService.refresh`。
- 新增 `GuardService`（前台服务，`foregroundServiceType="specialUse"` + `FOREGROUND_SERVICE_SPECIAL_USE` 权限 + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` 属性）：
  有启用闹钟才常驻（IMPORTANCE_LOW 通知「恶魔闹铃 · 守护中 · 下一个闹钟 …」），每 60s 刷新通知、每 3 分钟 `scheduleAll()` 自愈，`onTaskRemoved` 继续守；
  启动被拒时 `stopSelf()`（避免「已 start 未 foreground」的 ANR）。
- `RingService`：`postEmergency(c,id)`（紧急通知渠道 `demon_emergency`，按 `soundMode` 决定声音/震动，设 ringing 标记 + 15s 看门狗）；
  `begin()` 按 `wantSound()/wantVibrate()` 分别启动音频/震动；`snoozeNow()` 先 `cancelBackup` 防止备用闹钟打断暂停；`startForegroundCompat()` 失败也走 `postEmergency`。
- `Alarm`：新增 `soundMode`（0 铃声+震动 / 1 仅铃声 / 2 仅震动，常量 `SOUND_BOTH/SOUND_ONLY/VIBRATE_ONLY`）、`wantsSound()/wantsVibrate()/soundModeText()`，JSON 存 `soundMode`（`optInt` 默认 0、clamp 0..2）。
- `EditActivity`：新建默认时间改为**下一分钟**；新增「响铃方式」三 chip（`buildSoundCard`）；保存后 `AlarmStore.schedule(this, alarm, GRACE_MS)` + Toast「闹钟已排：今天 14:31 · 还有 3 秒」；没给精确闹钟权限时弹窗引导。
- `MainActivity`：列表每行显示「下次 今天 07:00 · 还有 8 小时」、非默认响铃方式追加「仅震动」；开关/删除后 `GuardService.refresh()`；`onResume` 也 refresh。
- `AndroidManifest.xml`：versionCode 2 / versionName 1.1，加 `FOREGROUND_SERVICE_SPECIAL_USE` 权限与 `GuardService` 声明（含 special-use 子类型 property）。

## v1.0 关键决策与理由
- **不用 Gradle / 不引 Maven**：沙箱里系统 curl/TLS 被拦（schannel `SEC_E_NO_CREDENTIALS`），只有 Node/Java 的 HTTPS 可用；
  依赖树下载不现实 → 手工链 `aapt2 → javac → d8 → pack(python) → zipalign → apksigner`。
- 工具链：`.android-sdk` = build-tools 34.0.0（aapt2/zipalign/lib/d8.jar/lib/apksigner.jar）+ platform-34/android.jar。
- JDK 用 `D:\CV\JAVA\JDK-23`（JDK-17 目录只有安装包，未装）。
- 包名 `com.demon.alarm`，minSdk 26 / targetSdk 34，纯框架 API（无 androidx），界面全部代码构建（不用 layout XML）。
- 图标：矢量自适应图标（`mipmap-anydpi-v26/ic_launcher.xml` + `drawable/ic_demon_fg.xml`）。

## 构建踩的两个坑（下次必看）
1. `javac` **必须 `-parameters`**：JDK 23 给合成桥接方法写 `MethodParameters` 且名字索引为 0，
   build-tools 34 的 R8 8.2 直接 NPE：`Cannot invoke "String.length()" because "<parameter1>" is null`（报在 `AlarmStore$1.class`）。
2. `javac` 用 **`-source 8 -target 8`**（Java 8 字节码），d8 才不报版本问题。
3. 沙箱下 Node `child_process` 管道 stdio 会 EPERM → build.mjs 里 spawn 一律 `stdio:'inherit'`。
4. `resources.arsc` 必须保持 **STORED 不压缩**（Android 11+ 要求）→ 用 `tools/pack.py` 按原 `compress_type` 重打包，别用 `jar uf`。
5. 源码里 `import android.provider.Settings` 会遮蔽项目自己的 `Settings` 类（编译报「找不到符号 defaultLevel」）。

## 交付内容（v1.1）
- `demon-alarm.apk`：包名 com.demon.alarm，versionCode 2 / versionName 1.1，minSdk 26，targetSdk 34，label 恶魔闹铃，launcher=MainActivity。
- 权限：WAKE_LOCK、VIBRATE、RECEIVE_BOOT_COMPLETED、SCHEDULE_EXACT_ALARM、USE_EXACT_ALARM、POST_NOTIFICATIONS、
  FOREGROUND_SERVICE(+MEDIA_PLAYBACK/SPECIAL_USE)、USE_FULL_SCREEN_INTENT、DISABLE_KEYGUARD、MODIFY_AUDIO_SETTINGS、RECORD_AUDIO、REQUEST_IGNORE_BATTERY_OPTIMIZATIONS。
- 19 个类 + R.java → dex 91056 字节；APK 内：classes.dex、AndroidManifest.xml、res/*、resources.arsc(STORED)、META-INF 签名。
- 构建校验：`aapt2 dump badging`（versionCode=2/versionName=1.1）、`aapt2 dump xmltree`（两个 service 的 foregroundServiceType：RingService=0x2 mediaPlayback，GuardService=0x40000000 specialUse + 子类型 property）、`zipalign -c -v 4`、`apksigner verify`（v2/v3 true，APK 57898 字节）。
- `README.md`：安装 3 步、首次 3 个授权、用法（含响铃方式）、7 重「关不掉」机制、已知限制（强行停止能杀/关机不响/国产 ROM 白名单）、重建说明。
- 签名：`build/demon.keystore`，别名 demon，密码 demon123（覆盖安装必须保留，v1.0 → v1.1 可直接覆盖）。

## 未做/后续可加
- 无真机/模拟器可测 → 逻辑经过静态审查（setAlarmClock 参数、备用闹钟去重、前台服务类型与权限、PendingIntent 不可变、API 版本守卫均已核对），但**没有跑过真机**。
- 后续可加：关机闹钟（RTC_WAKEUP + 开机恢复）、按闹钟自定义铃声、音量渐强曲线、过关统计。

## v1.2（2026-10-25）

用户反馈两件事，都已处理：

1. **锁屏后响铃正常、震动不震**
   - `RingService.startVibration()` 改为 **`USAGE_ALARM`** 通道：API 33+ 用 `VibrationAttributes.Builder().setUsage(USAGE_ALARM)`，
     API 26-32 用 `AudioAttributes(USAGE_ALARM, CONTENT_TYPE_SONIFICATION)`，再不行退普通 `vibrate(effect)`。
     原因：默认 usage 会被静音 / 勿扰 / 部分 ROM 的锁屏策略静默丢掉。
   - `initVibrator()`：API 31+ 走 `VibratorManager.getDefaultVibrator()`，否则回退 `VIBRATOR_SERVICE`。
   - `Vibrator.isVibrating()` 不是公开 API（javac 报 cannot find symbol），改用自有 `vibrating` 标志 + tick 里每 30 秒重挂一次。
   - `ensureRinging()` 里调用 `ensureVibration()`；`shutdown()` 清 `vibrating`。
   - 提示：若闹钟「响铃方式」= 仅铃声，本来就不震。

2. **一次响铃只有一个关卡**
   - `Alarm.chainCount`（1..5，默认 2，JSON 往返 + clamp）。
   - `Challenges.create(..., chain)` 换成 **`Challenges.sequence(c, a, level, count)`**：返回连闯队列，
     相邻两关不同类型（沿用 prefs "last_challenge" 避重），每 2 关难度 +1（上限 5）。
   - `RingActivity` 用 `List<Challenge> stages` + `stageIndex`，`showStage(i)` / `onStageSolved()` 逐关推进，
     顶部标签「第 i / N 关 · 关卡名」；全部过完才 `onSolved()`。
   - 删除 `ChainChallenge.java`（不再引用）。
   - `EditActivity` 新增「一次响铃要连过几关」－/＋（1–5），`syncChips()` 显示「连过 N 关」，`save()` 写入 `chainCount`。
   - 列表副标题由 `Alarm.challengeText()` 自动附带「· 连过N关」。

3. **顺手修的构建问题**
   - `build.mjs` 每次 `rmrf(build)` 会把 keystore 一起删掉 → 每次签名不同、无法覆盖安装。
     现在 keystore 固定在 **`signing/demon.keystore`**。
   - 版本号真正来源是 `AndroidManifest.xml` 的 `versionCode/versionName`（`aapt2 link --version-code` 只是缺省值），
     两处都要改才生效：现在是 **versionCode 3 / versionName 1.2**。
   - 1.1 → 1.2 因签名丢失需先卸载；1.2 起可覆盖安装。

4. 构建结果：`node build.mjs` 全绿，APK **56.5 KB**，v2/v3 签名 `true`，
   cert SHA-256 `529819e5f2bda46d260d2a4c772ef1a29a66111375616f9d527602a017ffd2ca`（固定）。

## v1.3（2026-10-25）

用户反馈：「仅震动也是30秒重新触发啊」——指仅震动模式下震动断断续续（震一小段 → 静很久 → 再震）。

修复（RingService.java）：
- 波形拆成两套常量：`PATTERN_ONLY = {0,900,90,900,90,1500,90}`（仅震动，间隔仅 90ms，接近连续）、
  `PATTERN_MIX = {0,800,300,800,300,1300,300}`（铃声+震动，跟着铃声节奏）。
- 新增 `long[]` → `int[] amplitudes`（ON 段 255 / OFF 段 0），用
  `VibrationEffect.createWaveform(pattern, amps, 0)` 显式满幅，不吃系统默认振动强度档位。
- 新增字段 `lastVibrateAt`；`ensureVibration()` 改为**按时间戳重挂**：仅震动 2.5 秒、带铃声 20 秒一次
  （`SystemClock.elapsedRealtime()` 判断），被 ROM 掐掉最多 2.5 秒接回。
- `tick()` 里原「每 30 秒 vibrating=false + startVibration()」整段删掉（交给 ensureVibration 的时间戳逻辑）。
- `begin()` / `shutdown()` 重置 `lastVibrateAt = 0`；`startVibration()` 失败时把异常写进 `AlarmStore.setLastError`。
- `import android.os.SystemClock` 新增。
- 版本升到 versionCode 4 / versionName 1.3（AndroidManifest.xml 与 build.mjs 两处同步）。

构建：`node build.mjs` 通过，APK 57898 字节，dex 92864 字节，v2/v3 签名 true，
cert SHA-256 仍是 529819e5…（与 1.2 相同 → 可直接覆盖安装，无需卸载）。

## v1.4（2026-10-25）

用户问「震动幅度这么高会不会对手机产生影响」→ 结论：255 只是 Android API 上限、不超马达规格，
真正代价是长时间连续满幅的耗电 / 发热 / 机械磨损。用户选择做成可调。

改动：
- `Settings.java` 新增 `vibrateLevel()`（0 标准 / 1 强(默认) / 2 最强）、`setVibrateLevel()`、
  `vibrateAmplitude()`（153 / 217 / 255）。键 `vibrate_level`，仍存在 PREF `demon_settings`。
- `RingService.startVibration()`：振幅由写死 255 改为 `Settings.vibrateAmplitude(this)`。
- `SettingsActivity.java`：新增「震动强度」卡片（标准 / 强 / 最强 三个 chip，选中红底），
  `sync()` 里统一刷 chip 样式；页脚版本号 1.0 → 1.4。
- 版本 versionCode 5 / versionName 1.4（AndroidManifest.xml + build.mjs）。
- 构建通过：APK 57898 字节、dex 94292 字节、v2/v3 true、cert 与 1.2/1.3 相同（覆盖安装升级）。

## v1.5（2026-10-25 · 换主题 + 新图标）

用户要求：「给闹钟搭配一个好看的主题，你这黑不溜秋的我都不想点进去，还有图标也换一下」。

改动：
- `UI.java` 整体重写为亮色暖调：`BG=#FDF6F1`、`BG_TOP=#FFF4EC`、`BG_BOTTOM=#FBE5DA`、`CARD=#FFFFFF`、
  `CARD2=#F7EFE9`、`STROKE=#EFDFD5`、`RED=#EA5237`、`RED_SOFT=#FFE9E2`、`TEXT=#221B1A`、`DIM=#8C7C74`、
  `GREEN=#1E9E64`、`GREEN_SOFT=#E1F5EA`、`BANNER_BG=#FFF4E3`、`BANNER_TEXT=#8A5300`、`LINK=#2F7AE5`；
  新增 `bg()`（TOP_BOTTOM 渐变）、`ringBg()`（TL_BR 深红渐变 `#3D1017→#180608→#2E0A1E`）、
  `card()`（白卡 + 细描边）、`chipOff()`、`roundAll()`；响铃页专用色 `RING_TEXT/RING_DIM/RING_ACCENT/RING_SOFT/RING_LINE/RING_OK`。
- `MainActivity`：渐变背景、标题 26sp+字距、⚙ 加 chip 底、白色卡片 + 2dp 阴影、横幅改暖黄 `BANNER_BG/BANNER_STROKE`、「下次」用 `LINK` 蓝。
- `EditActivity`：渐变背景、`card()`、预设 chip 用 `chipOff()`、备注框 `STROKE`；`syncChips()` 天 chip 选中红底白字 / 未选 `chipOff`+`DIM`，关卡与响铃方式 chip 选中 `GREEN_SOFT`+`GREEN`。
- `SettingsActivity`：渐变背景、白卡、震动强度 chip 未选中改 `chipOff`+`DIM`，页脚版本 1.5。
- `RingActivity`：`ringBg()` 深红渐变；brand→`RING_ACCENT`，clock/stageLabel→`RING_TEXT`，status/footHint→`RING_DIM`，
  暂停按钮改 `roundStroke(RING_SOFT, RING_LINE)`，成功态→`RING_OK`；**`host` 垫白卡 + 18dp padding**（否则深色关卡文字在深红上看不清）。
- 关卡视图：`MathChallenge`/`MemoryChallenge`/`TypeChallenge` 输入框描边 `0xFF2C3040 → UI.STROKE`；
  `MemoryChallenge` 隐藏数字色 `UI.CARD2 → UI.CARD`（白卡上才隐形）；`TypeChallenge` 目标句改 `RED_DARK` + `CARD2` 底。
- 图标：新增 `app/res/drawable/ic_launcher_bg.xml`（108×108，aapt 线性渐变 `#FFC978→#F98A5E→#DD3F3A` + 左上径向高光）
  与 `ic_launcher_fg.xml`（白色闹钟：两个 r=7 铃铛在 (37,32)/(71,32)、双腿 stroke 7、身体 r=23、内圈 `#FFD8CE` 2.5、指针 `#E0453C` 4.5、中心 r=3.5）；
  `mipmap-anydpi-v26/ic_launcher.xml` 的 background/foreground 指向这两个 drawable。
- `colors.xml` / `styles.xml`：`DemonTheme` 父主题改 `Theme.Material.Light.NoActionBar`，`windowLightStatusBar/NavigationBar=true`、状态栏 `bg_top`、导航栏 `bg_bottom`；
  `DemonRingTheme` 保留深色父主题，`windowBackground=@color/ring_bg`、`textColorPrimary=@color/ring_text`。
- 版本 versionCode 6 / versionName 1.5（AndroidManifest.xml + build.mjs 同步）。

构建：`node build.mjs`（用 pwsh 跑；ops_bash 的 stdout 这轮吞掉了输出）通过 —— APK 60.8 KB、v2/v3 true、
cert SHA-256 仍是 529819e5…（与 1.2–1.4 相同 → 直接覆盖安装）。`aapt2 dump badging` 确认 versionCode 6 / versionName 1.5，
APK 内含 `res/drawable/ic_launcher_bg.xml`、`ic_launcher_fg.xml`、`mipmap-anydpi-v26/ic_launcher.xml`。

视觉自检（无真机）：用 Pillow 按真实色值画了两张示意图 —— `preview-icon-1.5.png`（图标方形/圆形遮罩）、
`preview-screens-1.5.png`（主界面 + 响铃页），确认对比度与排版没问题；
生成脚本 `tools/preview_icon.py`（任意目录可跑，输出到项目根）与 `tools/preview_screens.py`（要在 `build/` 里跑，
输出 `build/screens-preview.png`；build 目录会被重建清空，脚本已另存到 tools/）。
仍未上真机验证：Light 父主题下 NumberPicker / Switch / EditText 的原生渲染。
