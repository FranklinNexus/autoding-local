# AutoDing 手机能力研究

本目录把“手机留在办公室、接电源、脱离电脑运行”和“Android 位置模拟能力”拆成独立实验。实验包与现有 AutoDing 使用不同包名，当前阶段验证 Android 能力及证据质量，尚未组成自动考勤流程。

截至 2026-11-17，实机结论以 [RESULTS.md](RESULTS.md) 的证据记录为准。编译成功、合成样例通过、定时器触发和官方考勤成功是四种不同结果。

本轮华为实机已验证：解锁状态和锁屏 PIN 页的窗口树可读，两种状态均能取得 `1080×2400` 截图缓冲区；专用合成位置源可以回读并被标记为 mock，测试源已清理、研究位置权限已撤销。

USB 连接下，`setAlarmClock` 单次对照在保持锁屏息屏时触发，比计划晚 `34ms`，同次窗口扫描与截图完成且编号匹配。普通 `setExactAndAllowWhileIdle` 在另一息屏试次中被系统后移，最终迟到 `2分55.970秒`。这些是本机单次能力测试，长期定时稳定性及其他机型仍待验收。

OCR 安装后首次推理在应用没有网络权限的条件下通过，正确读取合成样例的 `09:18` / `18:22` 实际时间。手机整机断网对照、真实截图 OCR、手机本机 PIN 输入、拔 USB 接墙充、隔夜、重启及动态下班闭环仍待验证或集成，当前结果尚未构成手机完全独立打卡能力。

## 当前产物

| 产物 | 包名 / 入口 | 本阶段输出 |
| --- | --- | --- |
| 原生能力探针 | `org.autoding.research` / AutoDing 能力研究 | 锁屏状态、窗口结构计数、截图接口结果、60 秒定时唤醒结果 |
| 位置能力探针 | 同上，专用 `autoding_research` provider | 模拟应用选择状态、合成位置注入与回读、mock 标记、清理结果 |
| 离线中文 OCR | `org.autoding.ocrprobe` / AutoDing OCR Research | 用户选图或内置样例的日期与实际上下班时间候选 |

两个 App 的最低系统版本均为 Android API 31，当前 target API 为 33。华为、荣耀、小米、OPPO、vivo、三星等机型的后台管理和锁屏控件需要分别验收；品牌相同也应记录具体型号、系统构建及补丁版本。

原生探针没有 PIN 存储或输入代码，没有自动点击、启动钉钉或写入考勤记录的动作。OCR App 使用独立的手工图片选择入口，尚未接通原生截图接口。两个 App 均未申请网络权限。

## 路径一：手机留在办公室独立运行

需要逐段验证：精确定时触发 → 唤醒与服务存活 → 保留 PIN 时解锁 → 打开目标页面 → 获取新画面 → 本机识别回执 → 核对官方考勤记录 → 按实际上班时间计算下班时间。

本研究已经提供前三段中的定时、唤醒和锁屏结构探针，以及独立 OCR 探针。手机本机 PIN 输入、两个 App 的能力整合、目标页面导航、无人值守确认与生产调度接入属于后续工作。尤其是手机重启后首次凭据解锁，需与日常息屏解锁分开验收；系统 Direct Boot 支持本身不代表目标 App 的登录数据已经可用。

### 原生探针操作

1. 安装研究 APK，打开“AutoDing 能力研究”。
2. 点击“打开无障碍设置”，在系统页面启用本研究 App 的无障碍服务，返回并刷新能力报告，检查 `accessibilityConnected=true`。
3. 在桌面、目标 App 页面和锁屏 PIN 页分别执行“检查当前窗口与截图能力”。本步骤只读取结构计数与截图可用性。
4. 点击“60 秒后运行一次唤醒检查”。首次可能进入精确闹钟授权页面，授权并返回后重新点击一次才会安排测试。
5. 测试息屏/锁屏状态；等待一次测试结束后回到 App 查看 `alarmScheduledAt`、`alarmFiredAt`、`alarmLocked`、`alarmScanRequested` 及后续截图结果。
6. 在使用墙上电源且 USB 已拔出的条件下重复测试，人工记录测试前后状态及时间。此步骤应与连电脑测试单列记录。

另有“60 秒系统闹钟接口对照”按钮，使用 `setAlarmClock`，报告 `alarmMode=alarm-clock`；普通检查使用 `setExactAndAllowWhileIdle`，报告 `alarmMode=exact-allow-while-idle`。对照测试需分别记录 App 请求时刻、系统有效目标（本轮由 `dumpsys` 观测）和实际触发时刻；探针自身未自动读取系统有效排程。系统闹钟接口可能在系统中显示下一闹钟信息。探针共用一个单次定时目标，安排下一次检查会替换前一次，按顺序完成对照。

`alarmScanRequested=true` 只表示向已连接的无障碍服务提交了扫描请求。扫描及截图异步完成，应继续核对以下字段：

- `alarmId` 与 `alarmFiredForId` 一致，触发记录对应本次计划；`alarmFiredForScheduledAt` 对应本次计划时间。
- `alarmScanId`、顶层 `scanId`、`windowScan.scanId`、`screenshot.scanId` 一致，窗口和截图来自同一次请求。
- 对应的 `requestedAt`、`completedAt` 记录请求和完成的墙上时钟；`scanRequestedElapsedRealtime`、`completedElapsedRealtime` 等单调时钟字段用于检查时序，避免只依赖系统时间。
- `screenshot.outcome=available` 才表示取得缓冲区；`captureTimestamp` 仅作为截图 API 的原始时钟字段保留，不与墙上时钟或 `elapsedRealtime` 直接相减。关联判断使用同次 `scanId`、请求/完成时间及同源的单调时钟字段。

新版开始扫描时清除上一请求的窗口、截图和错误字段；迟到回调按 `scanId` 丢弃，避免覆盖更新请求的结果。旧版报告缺少这些关联字段，旧版成功请求仅作为当时触发和提交请求的证据。

本轮华为实机还观察到：锁屏后台直接通过 shell 广播读取报告时返回 `result=0`；先启动研究 App 的 `MainActivity` 后，以显式 action 调用可读取成功。广播失败原因仍待日志诊断，尚未确定为华为系统拦截。判断闹钟是否触发应使用原生保存的 `alarmFiredAt` 等字段，避免将 shell 入口失败当作闹钟失败；`alarmInteractiveBeforeWake` 记录唤醒前的交互状态，配合锁屏状态单独验证息屏测试条件。

精确闹钟权限与触发时刻应分开验收：本轮普通接口保持息屏的对照中，`dumpsys` 显示请求的 `origWhen` 保留原计划时间，但 `whenElapsed` 已后移 `175889ms`，尽管 `window=0`、`exactAllowReason=permission`。具体系统/OEM 根因待诊断。系统闹钟接口的另一次对照中，系统有效目标与 App 请求的单调时钟目标相等，实际触发晚 `34ms`；该结果仅计为一次通过，尚未完成隔夜、重复运行或跨机型稳定性验收。

| 字段 | 解释与验收重点 |
| --- | --- |
| `locked` / `secure` / `interactive` / `powered` | 当前锁屏、安全凭据、交互及接电状态 |
| `rootAvailable` / `scanComplete` | 窗口树是否可读、是否在 1024 节点上限内完成遍历 |
| `emptyFocusedPinEntries` / `digitCount` / `pinUiCandidate` | 空且聚焦的密码控件及十个数字键的结构候选；候选成立仍未执行解锁 |
| `attendanceTabCount` / `punchLabelCount` | 目标页面结构计数；计数为正尚不证明存在有效回执 |
| `screenshot.outcome` | `available` 表示得到图像缓冲区；`rejected` 连同 `errorCode` 记录系统结果 |
| `imagePersisted=false` | 截图缓冲区在回调中释放，未保存图像 |
| `hasPinStorage=false` / `hasNetworkPermission=false` | 本研究包的实现范围 |
| `alarmId` / `alarmFiredForId` / `alarmScanId` | 定时计划、实际触发及其扫描请求的关联 |
| `scanId` / `requestedAt` / `completedAt` | 每次扫描与异步完成结果的编号、请求及完成时间 |
| `alarmInteractiveBeforeWake` | 闹钟唤醒前屏幕是否处于交互状态；`false` 用于息屏条件核对 |

截图接口可能因安全窗口、权限或调用间隔返回错误。保留错误码与具体测试状态，不将截屏失败解释为解锁失败，也不将亮屏解释为解锁成功。

### 离线 OCR 操作

1. 打开“AutoDing OCR Research”，点击“运行内置合成样例（可断网测试）”。样例使用固定的 `2026-11-05 18:25:00 +08:00` 时钟，验证读取实际 `09:18` / `18:22`，而非班次标签 `09:00` / `18:18`。
2. 断开网络后重新启动 App，再运行样例，单独记录首次离线推理结果。内置依赖为 `com.google.mlkit:text-recognition-chinese:16.0.1`，APK 内携带中文模型，未选用动态下载模型的 Play Services 版本。
3. 点击“选择一张本机图片”，由 Android 文档选择器选图。真实图片按手机当前日期和时区解析，测试时将手机日期、时间和时区与办公室实际情况对齐。
4. 核对候选结果与原图、官方记录。识别失败或出现 `unsupported-layout` 时保持待核实。

当前裁剪区域仅对应 `1080×2400` 华为竖屏参考布局，支持相同比例缩放，宽高比偏差超过 `0.005` 时停止识别。即使宽高比相同，字体、显示缩放、导航栏或页面改版也可能让裁剪区域偏移，仍需按布局验证。选图任一边超过 5000 像素时退出读取。

解析器检查当天日期与星期、页面动作、上下班卡片标签、“已打卡”实际时间、未来时间、重复或冲突记录及先后顺序。它仅返回时间和状态，不保存图片、原始 OCR 文本，也不向生产 AutoDing 回传记录。手工选图尚未绑定“刚刚采集的新页面”，因此同日旧截图的内容也可能成为候选；后续接入调度时必须增加采集时间和页面新鲜度证据。

华为首次安装后的合成样例推理已显示班次日期 `2026-11-05`、实际上班 `09:18`、实际下班 `18:22`。安装包及设备包信息均未含 `INTERNET` / `ACCESS_NETWORK_STATE` 权限。本次保留手机原有网络状态，因此仅将“应用无网络权限下首次推理”计为通过；整机主动断网后的对照仍单列待测。

## 路径二：Android 位置模拟能力

当前实验回答“此系统能否为选定研究 App 创建一个专用模拟位置源，并观察 mock 标记”。它不读取手机实际坐标，不替换 `gps`、`network`、`fused` 等标准位置源，不向钉钉传入位置。专用源成功也不证明其他 App 会使用该源，更不证明考勤平台接受该位置。

1. 打开研究 App，点击“授予本实验位置权限”，选择精确位置权限。
2. 在系统开发者选项中，将“选择模拟位置信息应用”设为“AutoDing 能力研究”。仅安装 App 或授予位置权限尚未完成此步骤。
3. 点击“检查位置模拟能力”，预期状态为 `ready-for-local-fixture`。
4. 点击“运行专用位置源实验”。它创建专用 `autoding_research` provider，写入内置合成坐标，读取该专用源一次，最后移除专用源。
5. 核对 `injectionRequested=true`、`readBack=true`、`isMock=true`、`cleanupSucceeded=true`、`testProviderPresent=false`。
6. 测试结束后将系统“模拟位置信息应用”恢复到测试前的选择；该系统设置不由探针自动改动。

`mock-marker-observed` 是预期能力结论，说明 Android 明确将样例标记为模拟位置。若出现 `test-provider-cleanup-required`，先执行下述 `stop` 操作并刷新检查，确认专用源已移除后结束测试。此时不应通过更改标准位置源来清理。

## 构建与本地验证

在仓库根目录使用 PowerShell 7。原生构建需要可用的 JDK、Android SDK Platform 35、Build Tools 35.0.0；OCR 构建另需 Gradle 8.14.3 和与 Android Gradle Plugin 8.13.2 相容的 JDK。脚本从 `ANDROID_HOME`、`ANDROID_SDK_ROOT` 或默认 SDK 目录解析 SDK，也支持显式传参。

```powershell
pwsh -File research/tools/Build-Research.ps1 -SdkPath 'PATH_TO_ANDROID_SDK'
pwsh -File research/tools/Build-OcrProbe.ps1 -GradlePath 'PATH_TO_GRADLE/gradle.bat' -SdkPath 'PATH_TO_ANDROID_SDK'
pwsh -File research/test/Test-LocationProbe.ps1
pwsh -File research/ocr/test/Test-ReceiptParser.ps1
```

原生 APK 输出为 `build/AutoDing-research.apk`，使用本地研究测试签名。OCR APK 输出为 `research/ocr/build/distribution/AutoDing-OcrProbe-debug.apk`，同目录生成 SHA-256 文件。它们均为研究构建。

OCR 首次构建需要下载 Gradle/Maven 依赖，构建机联网与手机 App 联网是独立条件。缓存完整时可给 OCR 构建脚本加 `-Offline`；构建脚本还会检查合并后的 APK 未含网络权限。

位置夹具测试使用 Java 测试替身验证分支、mock 标记、异常路径和专用源清理；OCR 夹具测试直接验证纯 Java 解析器。这些测试不调用真实手机或 OCR 引擎。位置夹具里的旧 API 分支测试也不扩大 APK 的 API 31 最低版本范围。

本轮原生研究 APK 构建、39 项位置夹具检查、OCR 最终构建和离线重建、APK 验签、合并 Manifest 的网络权限检查、内置模型及 ARM 库核查、21 项回执解析检查均已通过。离线重建证明构建缓存完整，与手机首次断网推理是独立验收项。

### 可选 ADB 研究接口

先用 `adb devices` 确认设备，再给每条命令指定 `-s SERIAL`。桥接接收器由 Android `DUMP` 权限保护，供本机 ADB 研究调用；普通第三方 App 不应据此假定可以调用生产接口。

```powershell
adb -s SERIAL shell am broadcast -a org.autoding.research.PROBE -n org.autoding.research/.ProbeBridgeReceiver
adb -s SERIAL shell am broadcast -a org.autoding.research.PROBE -n org.autoding.research/.ProbeBridgeReceiver --es operation scan
adb -s SERIAL shell am broadcast -a org.autoding.research.PROBE -n org.autoding.research/.ProbeBridgeReceiver --es operation alarm
adb -s SERIAL shell am broadcast -a org.autoding.research.PROBE -n org.autoding.research/.ProbeBridgeReceiver --es operation alarmclock
adb -s SERIAL shell am broadcast -a org.autoding.research.PROBE -n org.autoding.research/.ProbeBridgeReceiver --es operation location
adb -s SERIAL shell am broadcast -a org.autoding.research.PROBE -n org.autoding.research/.ProbeBridgeReceiver --es operation stop
```

示例统一使用显式 action `org.autoding.research.PROBE`。省略 `operation` 时返回报告；`scan` 提交异步窗口与截图检查，`alarm` 安排 60 秒后的普通单次检查，`alarmclock` 安排系统闹钟接口的单次对照，`location` 运行专用位置源实验，`stop` 仅清理该专用位置源。ADB 控制用于研究观测，不计入“脱离电脑运行”的验收证据。

## 后续验收门槛

| 阶段 | 通过条件 | 本阶段范围 |
| --- | --- | --- |
| E1 锁屏和截图 | 指定机型的窗口结构、PIN 页候选与截图结果明确 | 本轮华为解锁及 PIN 页通过；目标回执内容与截图失败路径待测 |
| E2 离线中文识别 | 应用无网络权限下首次推理成功；整机断网对照、真实布局与反例分别验证 | 华为首次合成推理通过；整机断网对照和真实图片待测 |
| E3 单次定时唤醒 | 锁屏息屏时按计划触发、扫描结束；墙上电源与 USB 拔出条件独立复测 | USB 连接下系统闹钟接口单次晚 34ms，同次扫描/截图通过；普通接口实测后移，拔 USB 接墙充待测 |
| E4 手机本机 PIN 解锁 | 单独设计用户当次启用、一次输入、失败即停止的实验；验证凭据保护及正确窗口 | 研究 App 尚未实现或测试；PC 安装解锁不计入 |
| E5 独立完整流程 | USB 拔出，墙上电源，定时至官方记录及新回执完整闭环；按实际上班时间决定下班时刻 | 真实截图 OCR 与动态下班闭环尚未集成 |
| E6 长时与故障 | 隔夜、断网恢复、服务终止、权限撤回、重启后首次解锁分别验证，失败保持待核实 | 待设计与验收 |
| L1 专用位置源 | 注入、回读、mock 标记和清理均有实机证据 | 本轮华为成功路径通过；已恢复 mock app-op 并撤销研究位置权限 |

记录结果时使用北京时间（Asia/Shanghai），附设备型号、Android API、系统版本、研究 APK SHA-256、前置权限、USB/电源/锁屏状态、预期与实际结果及脱敏证据路径。PIN、完整私人页面、姓名、组织信息和真实坐标不进入公开日志或仓库。

## 参考

- [Android AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
- [Android Direct Boot](https://developer.android.com/privacy-and-security/direct-boot)
- [Android KeyguardManager](https://developer.android.com/reference/android/app/KeyguardManager)
- [ML Kit 中文文字识别](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
